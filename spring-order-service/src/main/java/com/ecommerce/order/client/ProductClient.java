package com.ecommerce.order.client;

import com.ecommerce.order.dto.inventory.*;
import com.ecommerce.order.dto.response.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "productClient", url = "${services.product.url}")
public interface ProductClient {
	@GetMapping("/api/products/{productId}")
	ProductResponse getProduct(@PathVariable Long productId);

	@GetMapping("/internal/api/inventory/{productId}")
	InventoryResponse getInventory(@PathVariable Long productId);

	@PostMapping("/internal/api/inventory/reserve")
	InventoryReservationResponse reserveInventory(@RequestBody InventoryReservationRequest request);

	@PostMapping("/internal/api/inventory/release")
	InventoryReservationResponse releaseInventory(@RequestBody InventoryOrderRequest request);
}
