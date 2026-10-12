package com.ecommerce.customercommand.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.customercommand.dto.response.CustomerResponse;
import com.ecommerce.customercommand.exception.CustomerNotFoundException;
import com.ecommerce.customercommand.exception.DuplicateEmailException;
import com.ecommerce.customercommand.exception.GlobalExceptionHandler;
import com.ecommerce.customercommand.exception.InvalidCustomerOperationException;
import com.ecommerce.customercommand.enums.CustomerStatus;
import com.ecommerce.customercommand.service.CustomerCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerCommandController.class)
@Import(GlobalExceptionHandler.class)
class CustomerCommandControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CustomerCommandService service;

    private final CustomerResponse response = new CustomerResponse(1L, "CUS-100001", "Anand", "Kumar",
            "anand@example.com", "9876543210", CustomerStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());

    @Test
    void register_returns201() throws Exception {
        when(service.registerCustomer(any())).thenReturn(response);
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated());
    }

    @Test
    void register_invalid_returns400() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicate_returns409() throws Exception {
        when(service.registerCustomer(any())).thenThrow(new DuplicateEmailException("anand@example.com"));
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isConflict());
    }

    @Test
    void update_returns200() throws Exception {
        when(service.updateCustomer(eq(1L), any())).thenReturn(response);
        mockMvc.perform(put("/api/customers/1").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk());
    }

    @Test
    void update_notFound_returns404() throws Exception {
        when(service.updateCustomer(eq(1L), any())).thenThrow(new CustomerNotFoundException(1L));
        mockMvc.perform(put("/api/customers/1").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(service).deleteCustomer(1L);
        mockMvc.perform(delete("/api/customers/1")).andExpect(status().isNoContent());
    }

    @Test
    void delete_alreadyDeleted_returns409() throws Exception {
        doThrow(new InvalidCustomerOperationException("Customer is already deleted")).when(service).deleteCustomer(1L);
        mockMvc.perform(delete("/api/customers/1")).andExpect(status().isConflict());
    }

    private Object request() { return java.util.Map.of("firstName", "Anand", "lastName", "Kumar", "email", "anand@example.com", "phone", "9876543210"); }
    @Test void nonNumericId_returns400() throws Exception {
        mockMvc.perform(delete("/api/customers/not-a-number")).andExpect(status().isBadRequest());
    }
    @Test void zeroId_returns400() throws Exception {
        mockMvc.perform(delete("/api/customers/0")).andExpect(status().isBadRequest());
    }
    @Test void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{"))
          .andExpect(status().isBadRequest());
    }
    @Test void unsupportedGet_returns405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/customers/1"))
          .andExpect(status().isMethodNotAllowed());
    }
}
