package com.ecommerce.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDelivery {
    private final JdbcTemplate jdbc;
    private final CustomerDirectory customers;
    private final JavaMailSender mail;
    private final String from;
    public EmailDelivery(JdbcTemplate jdbc, CustomerDirectory customers, JavaMailSender mail,
                         @Value("${notifications.from}") String from) {
        this.jdbc = jdbc; this.customers = customers; this.mail = mail; this.from = from;
    }
    @Transactional
    public void sendNext() {
        var jobs = jdbc.queryForList("""
            SELECT * FROM notification_inbox WHERE sent_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
            ORDER BY next_attempt_at, created_at LIMIT 1 FOR UPDATE SKIP LOCKED
            """);
        if (jobs.isEmpty()) return;
        var job = jobs.getFirst();
        Object id = job.get("event_id");
        try {
            String recipient = customers.emailFor(((Number) job.get("customer_id")).longValue());
            var message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(recipient);
            message.setSubject("Order placed successfully - " + job.get("order_number"));
            message.setText("Thank you! Your order " + job.get("order_number")
                    + " has been placed successfully and stock has been reserved.\n"
                    + "Order total: " + job.get("total_amount")
                    + "\nThis confirms order placement, not payment or delivery.\n\nFood Delivery Team");
            mail.send(message);
        } catch (RuntimeException failure) {
            // Persist retries without storing SMTP credentials, recipient addresses or provider error text.
            int attempts = ((Number) job.get("attempts")).intValue() + 1;
            long delaySeconds = Math.min(300L, 5L * (1L << Math.min(attempts, 6)));
            jdbc.update("""
                UPDATE notification_inbox SET attempts=?, next_attempt_at=?, last_error=? WHERE event_id=?
                """, attempts, java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(delaySeconds)),
                    failure.getClass().getSimpleName(), id);
            return;
        }

        // Keep database failures separate from delivery failures. If this update fails,
        // the transaction must roll back instead of trying to schedule a retry in the
        // same failed transaction.
        jdbc.update("UPDATE notification_inbox SET sent_at=CURRENT_TIMESTAMP, last_error=NULL WHERE event_id=?", id);
    }
}
