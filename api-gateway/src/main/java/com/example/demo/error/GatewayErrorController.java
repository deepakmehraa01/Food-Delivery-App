package com.example.demo.error;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.cloud.gateway.server.mvc.common.MvcUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

@RestController
public class GatewayErrorController implements ErrorController {
    private final GatewayErrors errors;
    private final MeterRegistry metrics;

    public GatewayErrorController(GatewayErrors errors, MeterRegistry metrics) {
        this.errors = errors;
        this.metrics = metrics;
    }

    @RequestMapping("/gateway/fallback")
    public ResponseEntity<GatewayErrors.ErrorBody> fallback(HttpServletRequest request) {
        Throwable failure = (Throwable) request.getAttribute(MvcUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        String code = "SERVICE_UNAVAILABLE";
        String message = "The requested service is temporarily unavailable";
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) {
                status = HttpStatus.GATEWAY_TIMEOUT;
                code = "GATEWAY_TIMEOUT";
                message = "The requested service did not respond in time";
                break;
            }
            if (cause instanceof CallNotPermittedException) {
                code = "CIRCUIT_OPEN";
            }
        }
        metrics.counter("gateway.upstream.failures", "code", code).increment();
        return ResponseEntity.status(status).body(errors.body(request, status, code, message));
    }

    @RequestMapping("/error")
    public ResponseEntity<GatewayErrors.ErrorBody> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = value instanceof Integer number ? HttpStatus.resolve(number) : null;
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(errors.body(request, status, status.name(),
                status.is5xxServerError() ? "An unexpected gateway error occurred" : status.getReasonPhrase()));
    }
}
