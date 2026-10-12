package com.ecommerce.order.mapper;

import com.ecommerce.order.dto.response.*;
import com.ecommerce.order.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
	public OrderResponse toResponse(Order o) {
		return new OrderResponse(o.getId(), o.getOrderNumber(), o.getCustomerId(), o.getStatus(), o.getTotalAmount(),
				o.getCreatedAt(), o.getUpdatedAt(),
				o.getItems().stream().map(i -> new OrderItemResponse(i.getProductId(), i.getProductName(),
						i.getUnitPrice(), i.getQuantity(), i.getTotalPrice())).toList());
	}
}
