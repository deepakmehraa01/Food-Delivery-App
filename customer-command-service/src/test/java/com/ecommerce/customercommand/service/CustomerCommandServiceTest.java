package com.ecommerce.customercommand.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.customercommand.dto.request.CustomerRegistrationRequest;
import com.ecommerce.customercommand.dto.request.CustomerUpdateRequest;
import com.ecommerce.customercommand.entity.Customer;
import com.ecommerce.customercommand.enums.CustomerStatus;
import com.ecommerce.customercommand.exception.CustomerNotFoundException;
import com.ecommerce.customercommand.exception.DuplicateEmailException;
import com.ecommerce.customercommand.exception.InvalidCustomerOperationException;
import com.ecommerce.customercommand.mapper.CustomerMapper;
import com.ecommerce.customercommand.repository.CustomerRepository;
import com.ecommerce.customercommand.service.impl.CustomerCommandServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerCommandServiceTest {
    @Mock CustomerRepository repository;
    private CustomerCommandService service;

    @BeforeEach
    void setUp() { service = new CustomerCommandServiceImpl(repository, new CustomerMapper()); }

    @Test
    void registerCustomer_success() {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest("Anand", "Kumar", "anand@example.com", "9876543210");
        Customer customer = new Customer(); customer.setStatus(CustomerStatus.ACTIVE);
        when(repository.existsByEmailIgnoreCase("anand@example.com")).thenReturn(false);
        when(repository.saveAndFlush(any(Customer.class))).thenReturn(customer);

        var response = service.registerCustomer(request);

        assertEquals(CustomerStatus.ACTIVE, response.status());
        verify(repository).saveAndFlush(any(Customer.class));
    }

    @Test
    void registerCustomer_duplicateEmail() {
        when(repository.existsByEmailIgnoreCase("anand@example.com")).thenReturn(true);
        assertThrows(DuplicateEmailException.class, () -> service.registerCustomer(
                new CustomerRegistrationRequest("Anand", "Kumar", "anand@example.com", "9876543210")));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateCustomer_success() {
        Customer customer = new Customer(); customer.setStatus(CustomerStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(customer));
        when(repository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(customer)).thenReturn(customer);

        service.updateCustomer(1L, new CustomerUpdateRequest("A", "K", "new@example.com", "9876543210"));

        assertEquals("new@example.com", customer.getEmail());
    }

    @Test
    void updateCustomer_customerNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(CustomerNotFoundException.class, () -> service.updateCustomer(1L,
                new CustomerUpdateRequest("A", "K", "new@example.com", "9876543210")));
    }

    @Test
    void updateCustomer_duplicateEmail() {
        Customer customer = new Customer(); customer.setStatus(CustomerStatus.ACTIVE);
        Customer other = new Customer();
        when(repository.findById(1L)).thenReturn(Optional.of(customer));
        when(repository.findByEmailIgnoreCase("other@example.com")).thenReturn(Optional.of(other));
        assertThrows(DuplicateEmailException.class, () -> service.updateCustomer(1L,
                new CustomerUpdateRequest("A", "K", "other@example.com", "9876543210")));
    }

    @Test
    void deleteCustomer_success() {
        Customer customer = new Customer(); customer.setStatus(CustomerStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(customer));
        service.deleteCustomer(1L);
        assertEquals(CustomerStatus.DELETED, customer.getStatus());
        verify(repository).save(customer);
    }

    @Test
    void deleteCustomer_customerNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(CustomerNotFoundException.class, () -> service.deleteCustomer(1L));
    }

    @Test
    void deleteCustomer_alreadyDeleted() {
        Customer customer = new Customer(); customer.setStatus(CustomerStatus.DELETED);
        when(repository.findById(1L)).thenReturn(Optional.of(customer));
        assertThrows(InvalidCustomerOperationException.class, () -> service.deleteCustomer(1L));
    }

    @Test
    void registerCustomer_concurrentDuplicateEmail() {
        when(repository.saveAndFlush(any(Customer.class))).thenThrow(emailConflict());
        assertThrows(DuplicateEmailException.class, () -> service.registerCustomer(
                new CustomerRegistrationRequest("A", "K", "new@example.com", "9876543210")));
    }

    @Test
    void updateCustomer_concurrentDuplicateEmail() {
        Customer customer = new Customer(); customer.setStatus(CustomerStatus.ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(customer));
        when(repository.saveAndFlush(customer)).thenThrow(emailConflict());
        assertThrows(DuplicateEmailException.class, () -> service.updateCustomer(1L,
                new CustomerUpdateRequest("A", "K", "new@example.com", "9876543210")));
    }

    @Test
    void registerCustomer_otherConstraintIsNotReportedAsDuplicateEmail() {
        var failure = new org.springframework.dao.DataIntegrityViolationException("duplicate number",
                new java.sql.SQLException("Key (customer_number)=(CUS-100001) already exists", "23505"));
        when(repository.saveAndFlush(any(Customer.class))).thenThrow(failure);
        org.junit.jupiter.api.Assertions.assertSame(failure, assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class, () -> service.registerCustomer(
                        new CustomerRegistrationRequest("A", "K", "new@example.com", "9876543210"))));
    }

    @Test
    void updateCustomer_responseIncludesFlushedTimestamp() {
        Customer customer = new Customer(); customer.setStatus(CustomerStatus.ACTIVE);
        var timestamp = java.time.LocalDateTime.of(2026, 10, 9, 14, 0);
        when(repository.findById(1L)).thenReturn(Optional.of(customer));
        when(repository.saveAndFlush(customer)).thenAnswer(invocation -> {
            org.springframework.test.util.ReflectionTestUtils.setField(customer, "updatedAt", timestamp);
            return customer;
        });
        var result = service.updateCustomer(1L,
                new CustomerUpdateRequest("A", "K", "new@example.com", "9876543210"));
        assertEquals(timestamp, result.updatedAt());
    }

    private org.springframework.dao.DataIntegrityViolationException emailConflict() {
        return new org.springframework.dao.DataIntegrityViolationException("email conflict",
                new java.sql.SQLException("Key (email)=(new@example.com) already exists", "23505"));
    }
}
