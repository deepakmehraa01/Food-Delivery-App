package com.ecommerce.order.controller;

import com.ecommerce.order.dto.request.CreateOrderRequest;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {
	private final OrderService service;

	@PostMapping
	public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
		OrderResponse r = service.createOrder(request);
		return ResponseEntity.created(URI.create("/api/orders/" + r.orderId())).body(r);
	}

	@GetMapping("/{orderId}")
	public OrderResponse get(@PathVariable @Positive Long orderId) {
		return service.getOrder(orderId);
	}

	@GetMapping
	public Page<OrderResponse> all(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.getOrders(PageRequest.of(page, size));
	}

	@GetMapping("/customer/{customerId}")
	public Page<OrderResponse> customer(@PathVariable @Positive Long customerId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.getOrdersByCustomer(customerId, PageRequest.of(page, size));
	}

	@PatchMapping("/{orderId}/cancel")
	public OrderResponse cancel(@PathVariable @Positive Long orderId) {
		return service.cancelOrder(orderId);
	}
}
