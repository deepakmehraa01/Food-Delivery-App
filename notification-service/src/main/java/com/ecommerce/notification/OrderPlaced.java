package com.ecommerce.notification;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(@NotNull UUID eventId, @Min(1) @Max(1) int schemaVersion,
        @NotNull @Positive Long orderId, @NotBlank String orderNumber,
        @NotNull @Positive Long customerId, @NotNull @DecimalMin("0") BigDecimal totalAmount,
        @NotNull Instant occurredAt) {}
