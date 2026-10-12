package com.ecommerce.order.dto.inventory;

public record InventoryResponse(Long productId, int totalQuantity, int reservedQuantity, int availableQuantity) {
}
