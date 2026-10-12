package com.ecommerce.customerquery.controller;

import com.ecommerce.customerquery.dto.response.CustomerPageResponse;
import com.ecommerce.customerquery.dto.response.CustomerResponse;
import com.ecommerce.customerquery.enums.CustomerStatus;
import com.ecommerce.customerquery.exception.CustomerNotFoundException;
import com.ecommerce.customerquery.exception.GlobalExceptionHandler;
import com.ecommerce.customerquery.service.CustomerQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerQueryController.class)
@Import(GlobalExceptionHandler.class)
class CustomerQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerQueryService service;

    @Test
    void getById_returns200() throws Exception {
        when(service.getCustomerById(101L)).thenReturn(response());

        mockMvc.perform(get("/api/customers/101"))
                .andExpect(status().isOk());
    }

    @Test
    void missingCustomer_returns404() throws Exception {
        when(service.getCustomerById(101L)).thenThrow(new CustomerNotFoundException("Customer not found with id: 101"));

        mockMvc.perform(get("/api/customers/101"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPage_returns400() throws Exception {
        when(service.getCustomers(anyInt(), anyInt(), any(), any())).thenThrow(
                new com.ecommerce.customerquery.exception.InvalidSearchParameterException("Page index must be zero or greater"));

        mockMvc.perform(get("/api/customers?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_returns200() throws Exception {
        when(service.searchCustomers(any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new CustomerPageResponse(List.of(response()), 0, 10, 1, 1, true, true));

        mockMvc.perform(get("/api/customers/search?keyword=Anand"))
                .andExpect(status().isOk());
    }

    private CustomerResponse response() {
        return new CustomerResponse(101L, "CUS-100101", "Anand", "Kumar", "anand@example.com",
                "9876543210", CustomerStatus.ACTIVE, null, null);
    }
    @Test void missingEmail_returns400() throws Exception {
        mockMvc.perform(get("/api/customers/email")).andExpect(status().isBadRequest());
    }
    @Test void unsupportedPost_returns405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/customers"))
          .andExpect(status().isMethodNotAllowed());
    }
}
