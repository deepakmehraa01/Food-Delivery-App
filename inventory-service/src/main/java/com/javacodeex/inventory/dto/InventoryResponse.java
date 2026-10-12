package com.javacodeex.inventory.dto;

public record InventoryResponse(
        String productCode,
        String productName,
        Integer availableQuantity,
        boolean available
) {
}
