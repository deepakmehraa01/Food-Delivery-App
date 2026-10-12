package com.ecommerce.notification;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmailDeliveryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CustomerDirectory customers = mock(CustomerDirectory.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final EmailDelivery delivery = new EmailDelivery(jdbc, customers, mail, "orders@example.test");
    private final UUID id = UUID.randomUUID();
    private void pending() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("event_id",id,"customer_id",101L,
                "order_number","ORD-123","total_amount",new BigDecimal("20.00"),"attempts",0)));
    }
    @Test void sendsToRegisteredEmailAndMarksDelivered() {
        pending(); when(customers.emailFor(101L)).thenReturn("customer@example.test");
        delivery.sendNext();
        verify(mail).send(argThat((SimpleMailMessage m) -> Arrays.equals(m.getTo(),new String[]{"customer@example.test"})
                && m.getSubject().contains("ORD-123") && m.getText().contains("20.00")));
        verify(jdbc).update(startsWith("UPDATE notification_inbox SET sent_at"),eq(id));
    }
    @Test void smtpFailureRetainsPendingJobAndSchedulesRetry() {
        pending(); when(customers.emailFor(101L)).thenReturn("customer@example.test");
        doThrow(new MailSendException("provider error")).when(mail).send(any(SimpleMailMessage.class));
        delivery.sendNext();
        verify(jdbc).update(contains("attempts=?"),eq(1),any(java.sql.Timestamp.class),eq("MailSendException"),eq(id));
        verify(jdbc,never()).update(startsWith("UPDATE notification_inbox SET sent_at"),eq(id));
    }
    @Test void unavailableCustomerDoesNotSendToAnotherRecipient() {
        pending(); when(customers.emailFor(101L)).thenThrow(new IllegalStateException("not found"));
        delivery.sendNext(); verifyNoInteractions(mail);
        verify(jdbc).update(contains("attempts=?"),eq(1),any(java.sql.Timestamp.class),eq("IllegalStateException"),eq(id));
    }
    @Test void emptyQueueDoesNotSend() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        delivery.sendNext(); verifyNoInteractions(mail,customers);
    }
}
