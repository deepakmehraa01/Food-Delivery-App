package com.ecommerce.order.service;

import com.ecommerce.order.client.ProductClient;
import com.ecommerce.order.dto.inventory.*;
import com.ecommerce.order.dto.response.ProductResponse;
import com.ecommerce.order.exception.*;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductIntegrationService {
	private final ProductClient client;

	@CircuitBreaker(name = "productRead", fallbackMethod = "productFallback")
	@Retry(name = "productRead", fallbackMethod = "productFallback")
	public ProductResponse getProduct(Long id) {
		try {
			return client.getProduct(id);
		} catch (FeignException.NotFound e) {
			throw new ProductNotFoundException(id);
		} catch (FeignException e) {
			throw new ProductServiceUnavailableException(e);
		}
	}

	@CircuitBreaker(name = "productRead", fallbackMethod = "inventoryFallback")
	@Retry(name = "productRead", fallbackMethod = "inventoryFallback")
	public InventoryResponse getInventory(Long id) {
		try {
			return client.getInventory(id);
		} catch (FeignException e) {
			throw new ProductServiceUnavailableException(e);
		}
	}

	@CircuitBreaker(name = "productWrite", fallbackMethod = "reserveFallback")
	public InventoryReservationResponse reserve(InventoryReservationRequest request) {
		try {
			return client.reserveInventory(request);
		} catch (FeignException e) {
			throw new ProductServiceUnavailableException(e);
		}
	}

	@CircuitBreaker(name = "productWrite", fallbackMethod = "releaseFallback")
	public InventoryReservationResponse release(InventoryOrderRequest request) {
		try {
			return client.releaseInventory(request);
		} catch (FeignException e) {
			throw new ProductServiceUnavailableException(e);
		}
	}

	private ProductResponse productFallback(Long id, Throwable t) {
		if (t instanceof ProductNotFoundException e)
			throw e;
		throw new ProductServiceUnavailableException(t);
	}

	private InventoryResponse inventoryFallback(Long id, Throwable t) {
		throw new ProductServiceUnavailableException(t);
	}

	private InventoryReservationResponse reserveFallback(InventoryReservationRequest r, Throwable t) {
		throw new ProductServiceUnavailableException(t);
	}

	private InventoryReservationResponse releaseFallback(InventoryOrderRequest r, Throwable t) {
		throw new ProductServiceUnavailableException(t);
	}
}
