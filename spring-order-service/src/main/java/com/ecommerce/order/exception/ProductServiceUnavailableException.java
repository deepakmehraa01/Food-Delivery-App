package com.ecommerce.order.exception;

public class ProductServiceUnavailableException extends RuntimeException {
	public ProductServiceUnavailableException() {
		super("Product Service is temporarily unavailable. Please try again later.");
	}

	public ProductServiceUnavailableException(Throwable t) {
		super("Product Service is temporarily unavailable. Please try again later.", t);
	}
}
