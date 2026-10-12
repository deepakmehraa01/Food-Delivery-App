package com.ecommerce.order.dto.inventory;

import java.util.List;

public record InventoryReservationResponse(Long orderId, List<Item> items) {
	public record Item(Long productId, int quantity, String status) {
	}
}
