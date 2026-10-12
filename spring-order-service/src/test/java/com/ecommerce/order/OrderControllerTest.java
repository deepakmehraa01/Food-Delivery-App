package com.ecommerce.order;

import com.ecommerce.order.controller.OrderController;
import com.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired MockMvc mvc; @MockBean OrderService service;
    @Test void invalidRequest_returnsBadRequest() throws Exception {
        mvc.perform(post("/api/orders").contentType("application/json").content("{\"customerId\":0,\"items\":[]}"))
            .andExpect(status().isBadRequest());
    }
}
