package com.ecommerce.customerquery.service.impl;

import com.ecommerce.customerquery.dto.response.CustomerPageResponse;
import com.ecommerce.customerquery.dto.response.CustomerResponse;
import com.ecommerce.customerquery.entity.CustomerReadModel;
import com.ecommerce.customerquery.enums.CustomerStatus;
import com.ecommerce.customerquery.exception.CustomerNotFoundException;
import com.ecommerce.customerquery.exception.InvalidSearchParameterException;
import com.ecommerce.customerquery.mapper.CustomerQueryMapper;
import com.ecommerce.customerquery.repository.CustomerQueryRepository;
import com.ecommerce.customerquery.service.CustomerQueryService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CustomerQueryServiceImpl implements CustomerQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerQueryServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final CustomerStatus HIDDEN_STATUS = CustomerStatus.DELETED;
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "customerNumber", "customerNumber", "firstName", "firstName", "lastName", "lastName",
            "email", "email", "createdAt", "createdAt", "updatedAt", "updatedAt");

    private final CustomerQueryRepository repository;
    private final CustomerQueryMapper mapper;

    public CustomerQueryServiceImpl(CustomerQueryRepository repository, CustomerQueryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long customerId) {
        LOGGER.info("Customer lookup requested by id");
        CustomerReadModel customer = repository.findByIdAndStatusNot(customerId, HIDDEN_STATUS)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + customerId));
        return mapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByCustomerNumber(String customerNumber) {
        CustomerReadModel customer = repository.findByCustomerNumberAndStatusNot(customerNumber, HIDDEN_STATUS)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with customer number: " + customerNumber));
        return mapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByEmail(String email) {
        CustomerReadModel customer = repository.findByEmailIgnoreCaseAndStatusNot(email, HIDDEN_STATUS)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with email: " + email));
        return mapper.toResponse(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPageResponse getCustomers(int page, int size, String sortBy, String direction) {
        Page<CustomerReadModel> result = repository.findByStatusNot(HIDDEN_STATUS,
                pageable(page, size, sortBy, direction));
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPageResponse getCustomersByStatus(CustomerStatus status, int page, int size) {
        Page<CustomerReadModel> result = repository.findByStatus(status, pageable(page, size, "createdAt", "desc"));
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerPageResponse searchCustomers(String keyword, CustomerStatus status, int page, int size,
                                                String sortBy, String direction) {
        validatePagination(page, size);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        Specification<CustomerReadModel> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status == null) {
                predicates.add(criteriaBuilder.notEqual(root.get("status"), HIDDEN_STATUS));
            } else {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (!normalizedKeyword.isBlank()) {
                String pattern = "%" + normalizedKeyword + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerNumber")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("phone")), pattern)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        Page<CustomerReadModel> result = repository.findAll(specification, pageable(page, size, sortBy, direction));
        LOGGER.info("Customer search executed; result count={}", result.getNumberOfElements());
        return toPageResponse(result);
    }

    private Pageable pageable(int page, int size, String sortBy, String direction) {
        validatePagination(page, size);
        String property = SORT_FIELDS.get(sortBy);
        if (property == null) {
            throw new InvalidSearchParameterException("Unsupported sort field: " + sortBy);
        }
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSearchParameterException("Direction must be ASC or DESC");
        }
        return PageRequest.of(page, size, Sort.by(sortDirection, property));
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidSearchParameterException("Page index must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidSearchParameterException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private CustomerPageResponse toPageResponse(Page<CustomerReadModel> page) {
        List<CustomerResponse> content = page.getContent().stream().map(mapper::toResponse).toList();
        return new CustomerPageResponse(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
