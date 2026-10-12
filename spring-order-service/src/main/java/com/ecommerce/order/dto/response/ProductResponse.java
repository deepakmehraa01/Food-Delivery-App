package com.ecommerce.order.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(Long id, String sku, String name, String description, BigDecimal price,
		ProductStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
	public enum ProductStatus {
		ACTIVE, INACTIVE, DELETED
	}
}
