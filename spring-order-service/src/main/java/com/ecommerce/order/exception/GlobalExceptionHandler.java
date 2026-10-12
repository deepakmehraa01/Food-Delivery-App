package com.ecommerce.order.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
	@ExceptionHandler(OrderNotFoundException.class)
	ResponseEntity<ErrorResponse> orderMissing(OrderNotFoundException e, HttpServletRequest r) {
		return error(404, "ORDER_NOT_FOUND", e.getMessage(), r);
	}

	@ExceptionHandler(ProductNotFoundException.class)
	ResponseEntity<ErrorResponse> productMissing(ProductNotFoundException e, HttpServletRequest r) {
		return error(404, "PRODUCT_NOT_FOUND", e.getMessage(), r);
	}

	@ExceptionHandler(InsufficientStockException.class)
	ResponseEntity<ErrorResponse> stock(InsufficientStockException e, HttpServletRequest r) {
		return error(409, "INSUFFICIENT_STOCK", e.getMessage(), r);
	}

	@ExceptionHandler(InvalidOrderStateException.class)
	ResponseEntity<ErrorResponse> state(InvalidOrderStateException e, HttpServletRequest r) {
		return error(409, "INVALID_ORDER_STATE", e.getMessage(), r);
	}

	@ExceptionHandler(ProductServiceUnavailableException.class)
	ResponseEntity<ErrorResponse> unavailable(ProductServiceUnavailableException e, HttpServletRequest r) {
		return error(503, "PRODUCT_SERVICE_UNAVAILABLE", e.getMessage(), r);
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, ConstraintViolationException.class,
			IllegalArgumentException.class })
	ResponseEntity<ErrorResponse> invalid(Exception e, HttpServletRequest r) {
		return error(400, "VALIDATION_ERROR", "Request validation failed", r);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ErrorResponse> optimistic(Exception e, HttpServletRequest r) {
		return error(409, "CONCURRENT_UPDATE", "Concurrent order update", r);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> unexpected(Exception e, HttpServletRequest r) {
		log.error("Unexpected order failure path={}", r.getRequestURI(), e);
		return error(500, "INTERNAL_ERROR", "An unexpected error occurred", r);
	}

	private ResponseEntity<ErrorResponse> error(int status, String code, String message, HttpServletRequest request) {
		return ResponseEntity.status(status)
				.body(new ErrorResponse(Instant.now(), status, code, message, request.getRequestURI()));
	}

	public record ErrorResponse(Instant timestamp, int status, String error, String code, String message, String path) {
		ErrorResponse(Instant t, int s, String c, String m, String p) {
			this(t, s, HttpStatus.valueOf(s).getReasonPhrase(), c, m, p);
		}
	}
}
