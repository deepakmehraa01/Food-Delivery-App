package com.ecommerce.order.service;

import com.ecommerce.order.dto.request.CreateOrderRequest;
import com.ecommerce.order.dto.response.OrderResponse;
import org.springframework.data.domain.*;

public interface OrderService {
	OrderResponse createOrder(CreateOrderRequest request);

	OrderResponse getOrder(Long id);

	Page<OrderResponse> getOrders(Pageable pageable);

	Page<OrderResponse> getOrdersByCustomer(Long customerId, Pageable pageable);

	OrderResponse cancelOrder(Long id);
}
