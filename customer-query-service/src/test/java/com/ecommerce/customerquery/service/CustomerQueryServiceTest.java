package com.ecommerce.customerquery.service;

import com.ecommerce.customerquery.dto.response.CustomerResponse;
import com.ecommerce.customerquery.entity.CustomerReadModel;
import com.ecommerce.customerquery.enums.CustomerStatus;
import com.ecommerce.customerquery.exception.CustomerNotFoundException;
import com.ecommerce.customerquery.mapper.CustomerQueryMapper;
import com.ecommerce.customerquery.repository.CustomerQueryRepository;
import com.ecommerce.customerquery.service.impl.CustomerQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerQueryServiceTest {

    @Mock
    private CustomerQueryRepository repository;

    private CustomerQueryService service;
    private CustomerReadModel customer;

    @BeforeEach
    void setUp() {
        service = new CustomerQueryServiceImpl(repository, new CustomerQueryMapper());
        customer = new CustomerReadModel(101L, "CUS-100101", "Anand", "Kumar",
                "anand@example.com", "9876543210", CustomerStatus.ACTIVE,
                LocalDateTime.of(2026, 10, 7, 10, 30), null);
    }

    @Test
    void getCustomerById_success() {
        when(repository.findByIdAndStatusNot(101L, CustomerStatus.DELETED)).thenReturn(Optional.of(customer));

        CustomerResponse response = service.getCustomerById(101L);

        assertEquals("CUS-100101", response.customerNumber());
    }

    @Test
    void getCustomerById_notFound() {
        when(repository.findByIdAndStatusNot(101L, CustomerStatus.DELETED)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> service.getCustomerById(101L));
    }

    @Test
    void getCustomerByEmail_success() {
        when(repository.findByEmailIgnoreCaseAndStatusNot("anand@example.com", CustomerStatus.DELETED))
                .thenReturn(Optional.of(customer));

        assertEquals(101L, service.getCustomerByEmail("anand@example.com").id());
    }

    @Test
    void getDeletedCustomer_notReturned() {
        when(repository.findByIdAndStatusNot(101L, CustomerStatus.DELETED)).thenReturn(Optional.empty());

        assertThrows(CustomerNotFoundException.class, () -> service.getCustomerById(101L));
        verify(repository).findByIdAndStatusNot(101L, CustomerStatus.DELETED);
    }

    @Test
    void searchCustomers_invalidPagination() {
        assertThrows(RuntimeException.class, () -> service.searchCustomers("Anand", null, 0, 101, "createdAt", "desc"));
    }
}
