package com.ecommerce.order.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "notifications.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);
    private final OutboxPublisher publisher;
    public OutboxScheduler(OutboxPublisher publisher) { this.publisher = publisher; }
    @Scheduled(fixedDelayString = "${notifications.poll-ms:1000}")
    public void tick() {
        try { publisher.publishNext(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { log.warn("Order notification remains queued; publisher will retry ({})", e.getClass().getSimpleName()); }
    }
}
