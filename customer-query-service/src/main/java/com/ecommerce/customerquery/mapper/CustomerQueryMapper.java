package com.ecommerce.customerquery.mapper;

import com.ecommerce.customerquery.dto.response.CustomerResponse;
import com.ecommerce.customerquery.entity.CustomerReadModel;
import org.springframework.stereotype.Component;

@Component
public class CustomerQueryMapper {

    public CustomerResponse toResponse(CustomerReadModel customer) {
        return new CustomerResponse(customer.getId(), customer.getCustomerNumber(),
                customer.getFirstName(), customer.getLastName(), customer.getEmail(),
                customer.getPhone(), customer.getStatus(), customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
