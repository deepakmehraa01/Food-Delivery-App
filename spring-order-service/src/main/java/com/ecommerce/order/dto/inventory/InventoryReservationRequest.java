package com.ecommerce.order.dto.inventory;

import jakarta.validation.Valid;
import java.util.List;

public record InventoryReservationRequest(Long orderId, List<Item> items) {
	public record Item(Long productId, Integer quantity) {
	}
}
