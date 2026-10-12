package com.ecommerce.order.service.impl;

import com.ecommerce.order.dto.inventory.*;
import com.ecommerce.order.dto.request.*;
import com.ecommerce.order.dto.response.ProductResponse;
import com.ecommerce.order.dto.response.OrderResponse;
import com.ecommerce.order.entity.*;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.exception.*;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.ProductIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
	private final OrderRepository orders;
	private final ProductIntegrationService productIntegration;
	private final OrderMapper mapper;

	@Override
	@Transactional(noRollbackFor = ProductServiceUnavailableException.class)
	public OrderResponse createOrder(CreateOrderRequest request) {
		rejectDuplicateItems(request);
		Order order = Order.builder().orderNumber(generateOrderNumber()).customerId(request.customerId())
				.status(OrderStatus.PENDING).totalAmount(BigDecimal.ZERO).build();
		BigDecimal total = BigDecimal.ZERO;
		List<InventoryReservationRequest.Item> reservationItems = new ArrayList<>();
		for (OrderItemRequest item : request.items()) {
			ProductResponse product = productIntegration.getProduct(item.productId());
			if (product.status() != ProductResponse.ProductStatus.ACTIVE)
				throw new InvalidOrderStateException("Product " + item.productId() + " is not active");
			var inventory = productIntegration.getInventory(item.productId());
			if (item.quantity() > inventory.availableQuantity())
				throw new InsufficientStockException(item.productId(), item.quantity(), inventory.availableQuantity());
			BigDecimal line = product.price().multiply(BigDecimal.valueOf(item.quantity()));
			order.addItem(OrderItem.builder().productId(product.id()).productName(product.name())
					.unitPrice(product.price()).quantity(item.quantity()).totalPrice(line).build());
			total = total.add(line);
			reservationItems.add(new InventoryReservationRequest.Item(item.productId(), item.quantity()));
		}
		order.setTotalAmount(total);
		Order saved = orders.saveAndFlush(order);
		try {
			productIntegration.reserve(new InventoryReservationRequest(saved.getId(), reservationItems));
			saved.setStatus(OrderStatus.INVENTORY_RESERVED);
			return mapper.toResponse(orders.saveAndFlush(saved));
		} catch (RuntimeException failure) {
			// A timeout is ambiguous: Product Service may have committed the idempotent
			// reservation.
			// Always issue a compensating release; an unknown reservation is safely
			// rejected there.
			releaseCompensation(saved.getId());
			saved.setStatus(OrderStatus.FAILED);
			orders.save(saved);
			throw failure;
		}
	}

	private void rejectDuplicateItems(CreateOrderRequest request) {
		Set<Long> ids = new HashSet<>();
		request.items().forEach(i -> {
			if (!ids.add(i.productId()))
				throw new IllegalArgumentException("Duplicate product IDs are not allowed");
		});
	}

	private String generateOrderNumber() {
		return "ORD-%s-%d-%04d".formatted(LocalDate.now(), System.currentTimeMillis(),
				ThreadLocalRandom.current().nextInt(10000));
	}

	private void releaseCompensation(Long orderId) {
		try {
			productIntegration.release(new InventoryOrderRequest(orderId));
		} catch (RuntimeException e) {
			log.error("Inventory compensation failed orderId={}", orderId, e);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public OrderResponse getOrder(Long id) {
		return mapper.toResponse(orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id)));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<OrderResponse> getOrders(Pageable pageable) {
		return orders.findAll(pageable).map(mapper::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<OrderResponse> getOrdersByCustomer(Long customerId, Pageable pageable) {
		return orders.findByCustomerId(customerId, pageable).map(mapper::toResponse);
	}

	@Override
	@Transactional
	public OrderResponse cancelOrder(Long id) {
		Order order = orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
		if (order.getStatus() == OrderStatus.CANCELLED)
			return mapper.toResponse(order);
		if (order.getStatus() == OrderStatus.FAILED || order.getStatus() == OrderStatus.CONFIRMED)
			throw new InvalidOrderStateException(
					"Order " + id + " cannot be cancelled from status " + order.getStatus());
		if (order.getStatus() == OrderStatus.INVENTORY_RESERVED)
			productIntegration.release(new InventoryOrderRequest(id));
		order.setStatus(OrderStatus.CANCELLED);
		return mapper.toResponse(orders.saveAndFlush(order));
	}
}
