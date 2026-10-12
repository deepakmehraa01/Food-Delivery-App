package com.ecommerce.order.exception;

public class InsufficientStockException extends RuntimeException {
	public InsufficientStockException(Long id, int requested, int available) {
		super("Insufficient stock for product " + id + ": requested " + requested + ", available " + available);
	}
}
