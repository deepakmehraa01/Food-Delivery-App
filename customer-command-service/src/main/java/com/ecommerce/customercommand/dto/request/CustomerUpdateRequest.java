package com.ecommerce.customercommand.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerUpdateRequest(
        @NotBlank(message = "First name is required") @Size(max = 100) String firstName,
        @NotBlank(message = "Last name is required") @Size(max = 100) String lastName,
        @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 255) String email,
        @NotBlank(message = "Phone is required") @Pattern(regexp = "^[+0-9() -]{7,20}$", message = "Phone must be valid") String phone
) { }
