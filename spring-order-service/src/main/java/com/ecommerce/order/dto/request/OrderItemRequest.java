package com.ecommerce.order.dto.request;

import jakarta.validation.constraints.*;

public record OrderItemRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) {
}
