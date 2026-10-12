package com.ecommerce.customercommand.dto.response;

import com.ecommerce.customercommand.enums.CustomerStatus;
import java.time.LocalDateTime;

public record CustomerResponse(Long id, String customerNumber, String firstName, String lastName,
                               String email, String phone, CustomerStatus status,
                               LocalDateTime createdAt, LocalDateTime updatedAt) { }
