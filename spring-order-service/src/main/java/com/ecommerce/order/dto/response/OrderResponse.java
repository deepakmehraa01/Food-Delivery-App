package com.ecommerce.order.dto.response;

import com.ecommerce.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long orderId, String orderNumber, Long customerId, OrderStatus status,
		BigDecimal totalAmount, LocalDateTime createdAt, LocalDateTime updatedAt, List<OrderItemResponse> items) {
}
