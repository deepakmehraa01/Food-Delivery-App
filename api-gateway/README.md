# API Gateway

Spring Cloud Gateway Server Web MVC, Java 25, port `8080`.

The gateway provides method-based routing, signed JWT validation, route permissions,
token-bucket rate limiting, per-route circuit breakers, HTTP timeouts, consistent
gateway errors, health probes, HTTP metrics, and Prometheus metrics.

## Run

Use Java 25 for Maven and the application:

For the JDK installed locally by the upgrade, run from this service directory:
`export JAVA_HOME="$PWD/../.jdk/jdk-25.0.2/jdk-25.0.2+10/Contents/Home"`.
The `java_home` command below is for a system-registered JDK installation.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export JWT_ISSUER_URI=https://your-auth-server.example
export JWT_JWK_SET_URI=https://your-auth-server.example/oauth2/jwks
export JWT_AUDIENCE=food-delivery-api
./mvnw spring-boot:run
```

No auth service exists in this workspace. The development defaults point to
`http://localhost:8081` and its `/oauth2/jwks` endpoint. The gateway can start and
serve public endpoints without the auth server, but protected requests need a
valid token from the configured issuer. Use the actual public JWKS URL published
by your provider; it may differ from `/oauth2/jwks`. Use HTTPS outside local development.

The gateway validates RS256 signatures against cached public signing keys, issuer,
audience, expiration, optional not-before time, and a required nonblank subject.
It does not issue tokens or implement login. Spring Security allows its standard
60-second clock skew during timestamp validation.

Example claims inside an issuer-signed JWT:

```json
{
  "iss": "https://your-auth-server.example",
  "aud": ["food-delivery-api"],
  "sub": "user-123",
  "exp": 1900000000,
  "roles": ["ADMIN"]
}
```

Send the token with `Authorization: Bearer <token>`. The `roles` claim maps to
Spring authorities with the `ROLE_` prefix. Unknown or absent roles do not grant
admin permissions. Expiration and subject are required even for signed tokens.

## Routes and permissions

| Request | Backend | Permission |
|---|---|---|
| POST `/api/customers` | Customer command, 8082 | Public registration |
| GET `/api/customers/**` | Customer query, 8087 | ADMIN |
| PUT/DELETE `/api/customers/**` | Customer command, 8082 | ADMIN |
| GET `/api/products/**` | Product, 8083 | Public |
| POST/PUT/DELETE `/api/products/**` | Product, 8083 | ADMIN |
| GET/POST/PATCH `/api/orders/**` | Order, 8084 | ADMIN |
| GET `/actuator/health` and `/actuator/health/**` | Gateway | Public, status only |
| `/actuator/info`, `/actuator/metrics/**`, `/actuator/prometheus` | Gateway | ADMIN |
| Internal inventory paths and other unmatched security paths | None | Denied |

Both `/api/customers` and its child paths are routed. The same applies to product
and order paths. A valid admin request with an unsupported routed HTTP method may
return `404` because no gateway route matches. Business API responses such as
`400`, `404`, and `409` from the backend pass through unchanged.

Customer management exposes global search and lookup, and the order service does
not yet verify ownership. Those APIs remain admin-only. Before allowing CUSTOMER
access, implement ownership checks inside the services using verified identity.
This also applies to creation requests containing a customer ID.

Deploy backend ports on a private network, or add JWT validation to the services,
so clients cannot bypass gateway permissions by calling ports 8082–8087 directly.
CSRF is disabled because the gateway uses stateless bearer tokens, not cookie login.

## Configuration

Configuration lives in `src/main/resources/application.properties`.

| Environment variable | Default |
|---|---|
| `CUSTOMER_QUERY_URL` | `http://localhost:8087` |
| `CUSTOMER_COMMAND_URL` | `http://localhost:8082` |
| `PRODUCT_SERVICE_URL` | `http://localhost:8083` |
| `ORDER_SERVICE_URL` | `http://localhost:8084` |
| `JWT_ISSUER_URI` | `http://localhost:8081` |
| `JWT_JWK_SET_URI` | `<issuer>/oauth2/jwks` |
| `JWT_AUDIENCE` | `food-delivery-api` |
| `GATEWAY_CONNECT_TIMEOUT` | `2s` |
| `GATEWAY_READ_TIMEOUT` | `3s` |
| `GATEWAY_RATE_LIMIT` | `60` requests per minute |
| `GATEWAY_REGISTRATION_RATE_LIMIT` | `5` registrations per minute |

Rate limiting uses Bucket4j with a bounded Caffeine cache. Authenticated requests
use the verified subject as their key; anonymous requests use the socket IP.
Registration has a separate, stricter bucket. Tokens replenish continuously.
Responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining`; rejected requests
also include `Retry-After` and return `429`. Health and metrics are not rate limited.
Invalid JWTs are rejected before entering these API buckets.

The limiter is local to one running gateway instance and resets on restart.
The cache holds at most 100,000 keys; eviction under high cardinality can reset a
client's bucket. Use a shared Bucket4j backend and suitable capacity before running
multiple replicas. Forwarded IP headers are ignored by default, preventing clients
from changing their rate-limit key. Behind a proxy, anonymous clients share the
proxy's socket IP until a trusted-proxy policy is configured.

## Circuit breakers and timeouts

Each route has its own Resilience4j circuit breaker. Defaults:

- Sliding window: 10 calls; evaluate after at least 5 calls.
- Open when at least 50% fail; stay open for 10 seconds.
- Allow 2 trial calls in half-open state before recovery.
- Connection errors, timeouts, and upstream `500/502/503/504` trigger failure handling.
- Backend client errors do not trip the circuit.

Open circuits fail fast with `503` without contacting the backend. Connection
failures and configured upstream server errors return a gateway `503`; timeouts
return `504`. Configure circuit settings using the standard
`resilience4j.circuitbreaker.configs.default.*` properties.

HTTP client connect/read timeouts bound upstream waits. The Resilience4j
TimeLimiter and bulkhead worker pool are disabled so servlet request handling
stays on the request thread. There are no automatic retries, especially for writes.
A timed-out write may already have completed at the backend; verify its outcome
before retrying. Errors after streaming response headers have been sent cannot
be replaced with a new JSON status/body.

## Gateway errors

Security failures, rate-limit failures, circuit fallbacks, and framework errors
use the following structure. Backend business error contracts are preserved.

```json
{
  "timestamp": "2026-10-10T12:00:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "code": "CIRCUIT_OPEN",
  "message": "The requested service is temporarily unavailable",
  "path": "/api/customers/1",
  "requestId": "generated-UUID"
}
```

Every response receives an `X-Request-ID`. Error responses contain the same ID.
Original request paths are retained across internal fallback/error dispatches.
Gateway responses do not expose exception messages, signing keys, or stack traces.

## Health and metrics

```text
GET /actuator/health
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/metrics                         (ADMIN token)
GET /actuator/metrics/http.server.requests    (ADMIN token)
GET /actuator/prometheus                      (ADMIN token)
```

Health probes describe the gateway process and readiness state. A downstream
outage does not mark the gateway itself unhealthy. Backend health checks belong
to each service's own monitoring.

Metrics include JVM/process metrics, HTTP request counts and latency,
`resilience4j.circuitbreaker.*`, `gateway.rate.limit.rejected`, and
`gateway.upstream.failures`. The last two appear after the corresponding event.
No user IDs or tokens are used as metric tags. Configure Prometheus with a trusted
admin bearer token to scrape `/actuator/prometheus`.

## Verify

```bash
./mvnw clean test
```

Integration tests use generated RSA keys and local JWKS/backend servers. They
cover routing, invalid signatures, expired tokens, wrong issuer/audience, route
permissions, public endpoints, isolated user buckets, registration limits,
spoofed forwarded IPs, timeouts, circuit opening/recovery, preserved business
errors, consistent errors, health probes, and protected metrics.
