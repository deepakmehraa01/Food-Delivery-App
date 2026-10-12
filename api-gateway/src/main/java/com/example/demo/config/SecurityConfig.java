package com.example.demo.config;

import com.example.demo.error.GatewayErrors;
import com.example.demo.filter.GatewayRateLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(@Value("${gateway.security.jwt.issuer}") String issuer,
                          @Value("${gateway.security.jwt.jwk-set-uri}") String jwkSetUri,
                          @Value("${gateway.security.jwt.audience}") String audience,
                          @Value("${spring.http.clients.connect-timeout}") Duration connectTimeout,
                          @Value("${spring.http.clients.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory keyRequests = new SimpleClientHttpRequestFactory();
        keyRequests.setConnectTimeout(connectTimeout);
        keyRequests.setReadTimeout(readTimeout);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(keyRequests)).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> jwt.getAudience().contains(audience)
                && jwt.getExpiresAt() != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                        "Required JWT claims are missing or invalid", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), claims));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, GatewayErrors errors, MeterRegistry metrics,
                                  @Value("${gateway.rate-limit.capacity}") long capacity,
                                  @Value("${gateway.rate-limit.registration-capacity}") long registrationCapacity,
                                  @Value("${gateway.rate-limit.period}") Duration period,
                                  @Value("${gateway.rate-limit.maximum-keys}") long maximumKeys) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            errors.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "A valid bearer token is required");
        };
        var denied = (org.springframework.security.web.access.AccessDeniedHandler) (request, response, ex) ->
                errors.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You do not have permission to access this resource");

        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        .requestMatchers("/api/customers", "/api/customers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        .requestMatchers("/api/products", "/api/products/**").hasRole("ADMIN")
                        // The current order service does not enforce customer ownership yet.
                        .requestMatchers("/api/orders", "/api/orders/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterAfter(new GatewayRateLimitFilter(errors, metrics, capacity, registrationCapacity,
                        period, maximumKeys), BearerTokenAuthenticationFilter.class)
                .build();
    }
}
