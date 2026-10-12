package com.ecommerce.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateOrderRequest(@NotNull @Positive Long customerId,
		@NotEmpty @Size(max = 100) List<@NotNull @Valid OrderItemRequest> items) {
}
