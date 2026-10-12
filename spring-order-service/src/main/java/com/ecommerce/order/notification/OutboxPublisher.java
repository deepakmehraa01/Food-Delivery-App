package com.ecommerce.order.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                           @Value("${notifications.topic}") String topic) {
        this.jdbc = jdbc; this.kafka = kafka; this.topic = topic;
    }
    @Transactional
    public void publishNext() throws Exception {
        var rows = jdbc.queryForList("""
            SELECT event_id, order_id, payload FROM order_notification_outbox WHERE published_at IS NULL
            ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED
            """);
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        // Wait for broker acknowledgement before marking published. A crash can replay the same event ID.
        kafka.send(topic, row.get("order_id").toString(), (String) row.get("payload")).get(15, TimeUnit.SECONDS);
        jdbc.update("UPDATE order_notification_outbox SET published_at=CURRENT_TIMESTAMP WHERE event_id=?", row.get("event_id"));
    }
}
