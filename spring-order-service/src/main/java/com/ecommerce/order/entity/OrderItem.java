package com.ecommerce.order.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "order_items")
public class OrderItem {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	@ToString.Exclude
	private Order order;
	@Column(nullable = false)
	private Long productId;
	@Column(nullable = false, length = 255)
	private String productName;
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal unitPrice;
	@Column(nullable = false)
	private Integer quantity;
	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal totalPrice;
}
