package com.ecommerce.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Validator validator;
    public OrderEventListener(JdbcTemplate jdbc, ObjectMapper json, Validator validator) {
        this.jdbc = jdbc; this.json = json; this.validator = validator;
    }
    @KafkaListener(topics = "${notifications.topic:orders.placed.v1}")
    public void receive(String payload) throws Exception {
        OrderPlaced event = json.readValue(payload, OrderPlaced.class);
        if (event == null || !validator.validate(event).isEmpty())
            throw new IllegalArgumentException("Invalid order event");
        // Commit the durable inbox before Kafka acknowledges the record. Replays are harmless.
        jdbc.update("""
            INSERT INTO notification_inbox(event_id, order_id, order_number, customer_id, total_amount)
            VALUES (?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
            """, event.eventId(), event.orderId(), event.orderNumber(), event.customerId(), event.totalAmount());
    }
}
