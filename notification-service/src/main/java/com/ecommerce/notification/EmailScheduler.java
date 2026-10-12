package com.ecommerce.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailScheduler {
    private final EmailDelivery delivery;
    public EmailScheduler(EmailDelivery delivery) { this.delivery = delivery; }
    @Scheduled(fixedDelayString = "${notifications.poll-ms:1000}")
    public void tick() { delivery.sendNext(); }
}
