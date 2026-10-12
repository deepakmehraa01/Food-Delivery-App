package com.ecommerce.customercommand.service;

import com.ecommerce.customercommand.dto.request.CustomerRegistrationRequest;
import com.ecommerce.customercommand.dto.request.CustomerUpdateRequest;
import com.ecommerce.customercommand.dto.response.CustomerResponse;

public interface CustomerCommandService {
    CustomerResponse registerCustomer(CustomerRegistrationRequest request);
    CustomerResponse updateCustomer(Long customerId, CustomerUpdateRequest request);
    void deleteCustomer(Long customerId);
}
