package com.ecommerce.order.notification;

import com.ecommerce.order.entity.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderNotificationOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public OrderNotificationOutbox(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Order order) {
        UUID id = UUID.randomUUID();
        try {
            String payload = json.writeValueAsString(Map.of("eventId", id, "schemaVersion", 1,
                    "orderId", order.getId(), "orderNumber", order.getOrderNumber(),
                    "customerId", order.getCustomerId(), "totalAmount", order.getTotalAmount(),
                    "occurredAt", Instant.now()));
            jdbc.update("INSERT INTO order_notification_outbox(event_id, order_id, payload) VALUES (?, ?, ?)",
                    id, order.getId(), payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize order notification", e);
        }
    }
}
