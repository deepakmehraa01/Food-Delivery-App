package com.ecommerce.customercommand.mapper;

import com.ecommerce.customercommand.dto.request.CustomerRegistrationRequest;
import com.ecommerce.customercommand.dto.request.CustomerUpdateRequest;
import com.ecommerce.customercommand.dto.response.CustomerResponse;
import com.ecommerce.customercommand.entity.Customer;
import com.ecommerce.customercommand.enums.CustomerStatus;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class CustomerMapper {
    public Customer toEntity(CustomerRegistrationRequest request) {
        Customer customer = new Customer();
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        customer.setPhone(request.phone().trim());
        customer.setStatus(CustomerStatus.ACTIVE);
        return customer;
    }

    public void updateEntity(Customer customer, CustomerUpdateRequest request) {
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(request.email().trim().toLowerCase(Locale.ROOT));
        customer.setPhone(request.phone().trim());
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getCustomerNumber(), customer.getFirstName(),
                customer.getLastName(), customer.getEmail(), customer.getPhone(), customer.getStatus(),
                customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
