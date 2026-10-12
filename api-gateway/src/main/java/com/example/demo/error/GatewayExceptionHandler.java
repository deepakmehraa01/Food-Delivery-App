package com.example.demo.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GatewayExceptionHandler {
    private final GatewayErrors errors;

    public GatewayExceptionHandler(GatewayErrors errors) {
        this.errors = errors;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GatewayErrors.ErrorBody> handle(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            return ResponseEntity.status(status).headers(error.getHeaders()).body(
                    errors.body(request, status, status.name(), status.getReasonPhrase()));
        }
        return ResponseEntity.internalServerError().body(errors.body(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR", "An unexpected gateway error occurred"));
    }
}
