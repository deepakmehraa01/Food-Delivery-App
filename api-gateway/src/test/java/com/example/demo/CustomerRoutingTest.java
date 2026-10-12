package com.example.demo;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerRoutingTest {
    private static final RSAKey KEY = key();
    private static final String ISSUER = "https://test-issuer.example";
    private static final AtomicInteger QUERY_CALLS = new AtomicInteger();
    private static final HttpServer QUERY = backend("query");
    private static final HttpServer COMMAND = backend("command");
    private static final HttpServer JWKS = jwks();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper json;
    @Autowired private CircuitBreakerRegistry breakers;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("CUSTOMER_QUERY_URL", () -> url(QUERY));
        registry.add("CUSTOMER_COMMAND_URL", () -> url(COMMAND));
        registry.add("PRODUCT_SERVICE_URL", () -> url(COMMAND));
        registry.add("ORDER_SERVICE_URL", () -> url(COMMAND));
        registry.add("gateway.security.jwt.issuer", () -> ISSUER);
        registry.add("gateway.security.jwt.jwk-set-uri", () -> url(JWKS) + "/jwks");
        registry.add("gateway.security.jwt.audience", () -> "test-api");
        registry.add("gateway.rate-limit.capacity", () -> "10");
        registry.add("gateway.rate-limit.registration-capacity", () -> "2");
        registry.add("gateway.rate-limit.period", () -> "10m");
        registry.add("spring.http.clients.connect-timeout", () -> "200ms");
        registry.add("spring.http.clients.read-timeout", () -> "200ms");
        registry.add("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls", () -> "2");
        registry.add("resilience4j.circuitbreaker.configs.default.sliding-window-size", () -> "2");
        registry.add("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state", () -> "30s");
    }

    @BeforeEach void resetBreakers() {
        breakers.getAllCircuitBreakers().forEach(breaker -> breaker.reset());
    }

    @Test void readsReachQueryAndWritesReachCommand() throws Exception {
        String admin = token("ADMIN");
        for (String path : new String[]{"/api/customers", "/api/customers/1"}) {
            for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
                var response = send(method, path, admin);
                assertEquals(200, response.statusCode(), method + " " + path + " " + response.body());
                assertEquals((method.equals("GET") ? "query" : "command") + ":" + method + ":" + path,
                        response.body());
            }
        }
    }

    @Test void missingAndInvalidTokensReturnConsistent401() throws Exception {
        assertError(send("GET", "/api/customers/1", null), 401, "UNAUTHORIZED", "/api/customers/1");
        var invalid = send("GET", "/api/customers/1", "not-a-jwt");
        assertError(invalid, 401, "UNAUTHORIZED", "/api/customers/1");
        assertEquals("Bearer", invalid.headers().firstValue("WWW-Authenticate").orElseThrow());
    }

    @Test void jwtRejectsExpiredWrongIssuerWrongAudienceAndWrongSignature() throws Exception {
        var now = Instant.now();
        String[] tokens = {
                sign(KEY, ISSUER, "test-api", now.minusSeconds(600), "ADMIN"),
                sign(KEY, "https://wrong.example", "test-api", now.plusSeconds(600), "ADMIN"),
                sign(KEY, ISSUER, "wrong-audience", now.plusSeconds(600), "ADMIN"),
                sign(key(), ISSUER, "test-api", now.plusSeconds(600), "ADMIN")
        };
        for (String token : tokens) {
            assertError(send("GET", "/api/customers/1", token), 401, "UNAUTHORIZED", "/api/customers/1");
        }
    }

    @Test void jwtRequiresExpiryAndSubjectAndDoesNotGrantUnknownRoles() throws Exception {
        for (boolean missingExpiry : List.of(true, false)) {
            var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("test-api")
                    .claim("roles", List.of("ADMIN"));
            if (missingExpiry) claims.subject("user-123");
            else claims.expirationTime(Date.from(Instant.now().plusSeconds(600)));
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims.build());
            jwt.sign(new RSASSASigner(KEY));
            assertError(send("GET", "/api/customers/1", jwt.serialize()), 401, "UNAUTHORIZED", "/api/customers/1");
        }
        assertError(send("GET", "/api/customers/1", token("UNKNOWN")), 403, "FORBIDDEN", "/api/customers/1");
    }

    @Test void permissionsProtectCustomerDataProductWritesAndMonitoring() throws Exception {
        String customer = token("CUSTOMER");
        assertError(send("GET", "/api/customers/1", customer), 403, "FORBIDDEN", "/api/customers/1");
        assertError(send("DELETE", "/api/products/1", customer), 403, "FORBIDDEN", "/api/products/1");
        assertError(send("GET", "/actuator/metrics", customer), 403, "FORBIDDEN", "/actuator/metrics");
        assertEquals(200, send("GET", "/api/products/1", null).statusCode());
        assertError(send("GET", "/api/orders/1", customer), 403, "FORBIDDEN", "/api/orders/1");
        assertEquals(200, send("GET", "/api/orders/1", token("ADMIN")).statusCode());
        assertError(send("GET", "/internal/api/inventory/1", token("ADMIN")), 403, "FORBIDDEN",
                "/internal/api/inventory/1");
        assertError(send("GET", "/gateway/fallback", token("ADMIN")), 403, "FORBIDDEN", "/gateway/fallback");
    }

    @Test void rateLimitReturns429AndIsolatesUsers() throws Exception {
        String first = token("ADMIN");
        for (int i = 0; i < 10; i++) assertEquals(200, send("GET", "/api/customers/1", first).statusCode());
        var limited = send("GET", "/api/customers/1", first);
        assertError(limited, 429, "RATE_LIMIT_EXCEEDED", "/api/customers/1");
        assertTrue(Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow()) > 0);
        assertEquals("0", limited.headers().firstValue("X-RateLimit-Remaining").orElseThrow());
        assertEquals(200, send("GET", "/api/customers/1", token("ADMIN")).statusCode());
    }

    @Test void anonymousRegistrationHasStricterLimitAndIgnoresSpoofedForwardedIp() throws Exception {
        for (int i = 0; i < 2; i++) assertEquals(200, send("POST", "/api/customers", null).statusCode());
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/customers"))
                .header("X-Forwarded-For", "203.0.113.99")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertError(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()), 429, "RATE_LIMIT_EXCEEDED", "/api/customers");
    }

    @Test void timeoutReturns504WithOriginalPath() throws Exception {
        var response = send("GET", "/api/customers/slow", token("ADMIN"));
        assertError(response, 504, "GATEWAY_TIMEOUT", "/api/customers/slow");
    }

    @Test void lostBackendConnectionReturns503() throws Exception {
        assertError(send("GET", "/api/customers/disconnect", token("ADMIN")), 503,
                "SERVICE_UNAVAILABLE", "/api/customers/disconnect");
    }

    @Test void repeatedFailuresOpenCircuitAndRecoveryClosesIt() throws Exception {
        String admin = token("ADMIN");
        assertError(send("GET", "/api/customers/failure", admin), 503, "SERVICE_UNAVAILABLE", "/api/customers/failure");
        assertEquals(503, send("GET", "/api/customers/failure", admin).statusCode());
        int calls = QUERY_CALLS.get();
        assertError(send("GET", "/api/customers/1", admin), 503, "CIRCUIT_OPEN", "/api/customers/1");
        assertEquals(calls, QUERY_CALLS.get(), "Open circuit must not contact the backend");
        // Advance the breaker explicitly to avoid a wall-clock-dependent test.
        breakers.circuitBreaker("customerQuery").transitionToHalfOpenState();
        assertEquals(200, send("GET", "/api/customers/1", admin).statusCode());
        assertEquals(200, send("GET", "/api/customers/1", admin).statusCode());
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED,
                breakers.circuitBreaker("customerQuery").getState());
    }

    @Test void businessErrorsPassThroughWithoutOpeningCircuit() throws Exception {
        String admin = token("ADMIN");
        for (int i = 0; i < 3; i++) {
            var response = send("GET", "/api/customers/conflict", admin);
            assertEquals(409, response.statusCode());
            assertEquals("business-conflict", response.body());
        }
        assertEquals(200, send("GET", "/api/customers/1", admin).statusCode());
    }

    @Test void healthProbesAndAdminMetricsAreAvailable() throws Exception {
        for (String path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            var response = send("GET", path, null);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("UP", json.readTree(response.body()).get("status").asText());
            assertFalse(response.body().contains("components"));
        }
        String admin = token("ADMIN");
        send("GET", "/api/customers/1", admin);
        var metrics = send("GET", "/actuator/metrics/http.server.requests", admin);
        assertEquals(200, metrics.statusCode(), metrics.body());
        var prometheus = send("GET", "/actuator/prometheus", admin);
        assertEquals(200, prometheus.statusCode());
        assertTrue(prometheus.body().contains("http_server_requests"));
        assertTrue(prometheus.body().contains("resilience4j_circuitbreaker"));
    }

    @Test void unmatchedApiPathUsesGatewayErrorFormat() throws Exception {
        assertError(send("PATCH", "/api/customers/1", token("ADMIN")), 404, "NOT_FOUND", "/api/customers/1");
    }

    private void assertError(HttpResponse<String> response, int status, String code, String path) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        var body = json.readTree(response.body());
        assertEquals(code, body.get("code").asText());
        assertEquals(path, body.get("path").asText());
        assertEquals(status, body.get("status").asInt());
        assertNotNull(body.get("timestamp"));
        assertEquals(response.headers().firstValue("X-Request-ID").orElseThrow(), body.get("requestId").asText());
    }

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(String role) throws Exception {
        return sign(KEY, ISSUER, "test-api", Instant.now().plusSeconds(600), role);
    }

    private static String sign(RSAKey key, String issuer, String audience, Instant expires, String role) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(UUID.randomUUID().toString())
                .issueTime(new Date()).expirationTime(Date.from(expires)).claim("roles", List.of(role)).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey key() {
        try { return new RSAKeyGenerator(2048).keyID("test-key").generate(); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static String url(HttpServer server) { return "http://localhost:" + server.getAddress().getPort(); }

    @AfterAll static void stopBackends() {
        CLIENT.close();
        QUERY.stop(0); COMMAND.stop(0); JWKS.stop(0);
    }

    private static HttpServer jwks() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start(); return server;
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }

    private static HttpServer backend(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", exchange -> {
                if (name.equals("query")) QUERY_CALLS.incrementAndGet();
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/disconnect")) {
                    exchange.close();
                    return;
                }
                if (path.endsWith("/slow")) {
                    try { Thread.sleep(650); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
                int status = path.endsWith("/failure") ? 503 : path.endsWith("/conflict") ? 409 : 200;
                byte[] body = (status == 409 ? "business-conflict" : name + ":" + exchange.getRequestMethod() + ":" + path)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
                catch (IOException ignored) { /* The timeout test deliberately closes the connection. */ }
            });
            server.start(); return server;
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
}
