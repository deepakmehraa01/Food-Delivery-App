package com.ecommerce.customercommand.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CustomerNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(CustomerNotFoundException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ErrorResponse> duplicate(DuplicateEmailException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "CUSTOMER_EMAIL_ALREADY_EXISTS", ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidCustomerOperationException.class)
    ResponseEntity<ErrorResponse> invalidOperation(InvalidCustomerOperationException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "INVALID_CUSTOMER_OPERATION", ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, errors);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ConstraintViolationException || ex instanceof MethodArgumentTypeMismatchException
                || ex instanceof HttpMessageNotReadableException) {
            return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request parameters", request, null);
        }
        if (ex instanceof org.springframework.web.ErrorResponse error) {
            HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
            String code = status == HttpStatus.METHOD_NOT_ALLOWED ? "METHOD_NOT_ALLOWED" : "INVALID_REQUEST";
            return ResponseEntity.status(status).headers(error.getHeaders()).body(new ErrorResponse(
                    LocalDateTime.now(), status.value(), status.getReasonPhrase().toUpperCase(), code,
                    error.getBody().getDetail(), request.getRequestURI(), null));
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message,
                                                   HttpServletRequest request, Map<String, String> errors) {
        return ResponseEntity.status(status).body(new ErrorResponse(LocalDateTime.now(), status.value(),
                status.getReasonPhrase().toUpperCase(), code, message, request.getRequestURI(), errors));
    }
}
