package com.ecommerce.order.entity;

import com.ecommerce.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number"))
public class Order {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Version
	private Long version;
	@Column(name = "order_number", nullable = false, unique = true, updatable = false)
	private String orderNumber;
	@Column(nullable = false)
	private Long customerId;
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal totalAmount;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OrderStatus status;
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
	@Column(nullable = false)
	private LocalDateTime updatedAt;
	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<OrderItem> items = new ArrayList<>();

	public void addItem(OrderItem item) {
		items.add(item);
		item.setOrder(this);
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
