package com.ecommerce.customercommand.service.impl;

import com.ecommerce.customercommand.dto.request.CustomerRegistrationRequest;
import com.ecommerce.customercommand.dto.request.CustomerUpdateRequest;
import com.ecommerce.customercommand.dto.response.CustomerResponse;
import com.ecommerce.customercommand.entity.Customer;
import com.ecommerce.customercommand.enums.CustomerStatus;
import com.ecommerce.customercommand.exception.CustomerNotFoundException;
import com.ecommerce.customercommand.exception.DuplicateEmailException;
import com.ecommerce.customercommand.exception.InvalidCustomerOperationException;
import com.ecommerce.customercommand.mapper.CustomerMapper;
import com.ecommerce.customercommand.repository.CustomerRepository;
import com.ecommerce.customercommand.service.CustomerCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.sql.SQLException;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerCommandServiceImpl implements CustomerCommandService {
    private final CustomerRepository repository;
    private final CustomerMapper mapper;

    @Override
    @Transactional
    public CustomerResponse registerCustomer(CustomerRegistrationRequest request) {
        log.info("Customer registration requested");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailIgnoreCase(email)) throw new DuplicateEmailException(email);
        Customer saved = saveAndFlush(mapper.toEntity(request), email);
        log.info("Customer registered with id {}", saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(Long customerId, CustomerUpdateRequest request) {
        log.info("Customer update requested for id {}", customerId);
        Customer customer = repository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        repository.findByEmailIgnoreCase(email).filter(existing -> !Objects.equals(existing.getId(), customerId))
                .ifPresent(existing -> { throw new DuplicateEmailException(email); });
        if (customer.getStatus() == CustomerStatus.DELETED) {
            throw new InvalidCustomerOperationException("Deleted customers cannot be updated");
        }
        mapper.updateEntity(customer, request);
        Customer saved = saveAndFlush(customer, email);
        log.info("Customer updated with id {}", customerId);
        return mapper.toResponse(saved);
    }

    private Customer saveAndFlush(Customer customer, String email) {
        try {
            // Flush here so constraint errors and @PreUpdate occur before creating the response.
            return repository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                // PostgreSQL identifies the conflicting columns even for existing Hibernate-named constraints.
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                        && sql.getMessage() != null && sql.getMessage().contains("Key (email)=")) {
                    throw new DuplicateEmailException(email);
                }
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public void deleteCustomer(Long customerId) {
        log.info("Customer deletion requested for id {}", customerId);
        Customer customer = repository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (customer.getStatus() == CustomerStatus.DELETED) {
            throw new InvalidCustomerOperationException("Customer is already deleted");
        }
        customer.setStatus(CustomerStatus.DELETED);
        repository.save(customer);
        log.info("Customer soft deleted with id {}", customerId);
    }
}
