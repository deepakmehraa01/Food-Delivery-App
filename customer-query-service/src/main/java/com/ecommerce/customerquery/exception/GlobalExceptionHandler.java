package com.ecommerce.customerquery.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(CustomerNotFoundException exception,
                                                        HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler({InvalidSearchParameterException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        String code = exception instanceof InvalidSearchParameterException
                ? "INVALID_PAGINATION" : "INVALID_REQUEST";
        return response(HttpStatus.BAD_REQUEST, code, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof org.springframework.web.ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            String code = status == HttpStatus.METHOD_NOT_ALLOWED ? "METHOD_NOT_ALLOWED" : "INVALID_REQUEST";
            return ResponseEntity.status(status).headers(error.getHeaders()).body(new ErrorResponse(
                    LocalDateTime.now(), status.value(), status.getReasonPhrase(), code,
                    error.getBody().getDetail(), request.getRequestURI()));
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(LocalDateTime.now(), status.value(),
                status.getReasonPhrase(), code, message, request.getRequestURI()));
    }

    public record ErrorResponse(LocalDateTime timestamp, int status, String error, String code,
                                String message, String path) {
    }
}
