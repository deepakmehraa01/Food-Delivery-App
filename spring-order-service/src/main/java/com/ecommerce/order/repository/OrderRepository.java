package com.ecommerce.order.repository;

import com.ecommerce.order.entity.Order;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
	Page<Order> findByCustomerId(Long customerId, Pageable pageable);
}
