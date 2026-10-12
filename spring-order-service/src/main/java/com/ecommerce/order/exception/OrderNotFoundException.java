package com.ecommerce.order.exception;

public class OrderNotFoundException extends RuntimeException {
	public OrderNotFoundException(Long id) {
		super("Order " + id + " was not found");
	}
}
