package com.ecommerce.customercommand.controller;

import com.ecommerce.customercommand.dto.request.CustomerRegistrationRequest;
import com.ecommerce.customercommand.dto.request.CustomerUpdateRequest;
import com.ecommerce.customercommand.dto.response.CustomerResponse;
import com.ecommerce.customercommand.service.CustomerCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.Objects;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/customers")
@Tag(name = "Customer Commands", description = "Customer registration, update, and soft-delete operations")
public class CustomerCommandController {
    private final CustomerCommandService service;

    @PostMapping
    @Operation(summary = "Register a customer")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Customer registered"), @ApiResponse(responseCode = "400", description = "Invalid request"), @ApiResponse(responseCode = "409", description = "Duplicate email")})
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody CustomerRegistrationRequest request) {
        CustomerResponse response = service.registerCustomer(request);
        URI location = Objects.requireNonNull(URI.create("/api/customers/" + response.id()));
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Update a customer")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Customer updated"), @ApiResponse(responseCode = "400", description = "Invalid request"), @ApiResponse(responseCode = "404", description = "Customer not found"), @ApiResponse(responseCode = "409", description = "Conflict")})
    public ResponseEntity<CustomerResponse> update(@PathVariable @Positive Long customerId, @Valid @RequestBody CustomerUpdateRequest request) {
        return ResponseEntity.ok(service.updateCustomer(customerId, request));
    }

    @DeleteMapping("/{customerId}")
    @Operation(summary = "Soft delete a customer")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Customer deleted"), @ApiResponse(responseCode = "404", description = "Customer not found"), @ApiResponse(responseCode = "409", description = "Customer already deleted")})
    public ResponseEntity<Void> delete(@PathVariable @Positive Long customerId) {
        service.deleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }
}
