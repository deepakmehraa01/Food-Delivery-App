package com.ecommerce.customerquery.controller;

import com.ecommerce.customerquery.dto.response.CustomerPageResponse;
import com.ecommerce.customerquery.dto.response.CustomerResponse;
import com.ecommerce.customerquery.enums.CustomerStatus;
import com.ecommerce.customerquery.service.CustomerQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customer Query", description = "Read-only customer operations")
public class CustomerQueryController {

    private final CustomerQueryService service;

    public CustomerQueryController(CustomerQueryService service) {
        this.service = service;
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get a customer by ID")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Customer found"),
            @ApiResponse(responseCode = "404", description = "Customer not found")})
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long customerId) {
        return ResponseEntity.ok(service.getCustomerById(customerId));
    }

    @GetMapping("/customer-number/{customerNumber}")
    @Operation(summary = "Get a customer by customer number")
    public ResponseEntity<CustomerResponse> getByCustomerNumber(@PathVariable String customerNumber) {
        return ResponseEntity.ok(service.getCustomerByCustomerNumber(customerNumber));
    }

    @GetMapping("/email")
    @Operation(summary = "Get a customer by email")
    public ResponseEntity<CustomerResponse> getByEmail(@RequestParam String email) {
        return ResponseEntity.ok(service.getCustomerByEmail(email));
    }

    @GetMapping
    @Operation(summary = "Get all non-deleted customers")
    public ResponseEntity<CustomerPageResponse> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return ResponseEntity.ok(service.getCustomers(page, size, sortBy, direction));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get customers by status")
    public ResponseEntity<CustomerPageResponse> getByStatus(
            @PathVariable CustomerStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.getCustomersByStatus(status, page, size));
    }

    @GetMapping("/search")
    @Operation(summary = "Search customers with optional status, pagination, and sorting")
    public ResponseEntity<CustomerPageResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return ResponseEntity.ok(service.searchCustomers(keyword, status, page, size, sortBy, direction));
    }
}
