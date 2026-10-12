package com.ecommerce.customerquery.service;

import com.ecommerce.customerquery.dto.response.CustomerPageResponse;
import com.ecommerce.customerquery.dto.response.CustomerResponse;
import com.ecommerce.customerquery.enums.CustomerStatus;

public interface CustomerQueryService {

    CustomerResponse getCustomerById(Long customerId);

    CustomerResponse getCustomerByCustomerNumber(String customerNumber);

    CustomerResponse getCustomerByEmail(String email);

    CustomerPageResponse getCustomers(int page, int size, String sortBy, String direction);

    CustomerPageResponse getCustomersByStatus(CustomerStatus status, int page, int size);

    CustomerPageResponse searchCustomers(String keyword, CustomerStatus status, int page, int size,
                                         String sortBy, String direction);
}
