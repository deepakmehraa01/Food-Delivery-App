package com.ecommerce.order;

import com.ecommerce.order.dto.inventory.*;
import com.ecommerce.order.dto.request.*;
import com.ecommerce.order.dto.response.ProductResponse;
import com.ecommerce.order.entity.Order;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.repository.OrderRepository;
import com.ecommerce.order.service.ProductIntegrationService;
import com.ecommerce.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orders; @Mock ProductIntegrationService product; @Mock OrderMapper mapper;
    @InjectMocks OrderServiceImpl service;
    @Test void createOrder_success_reservesAfterAvailabilityCheck() {
        var p=new ProductResponse(10L,"SKU","Phone",null,new BigDecimal("10.00"), ProductResponse.ProductStatus.ACTIVE,null,null);
        when(product.getProduct(10L)).thenReturn(p); when(product.getInventory(10L)).thenReturn(new InventoryResponse(10L,5,0,5));
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(i-> { Order o=i.getArgument(0); if(o.getId()==null)o.setId(7L); return o; });
        when(mapper.toResponse(any())).thenReturn(null);
        service.createOrder(new CreateOrderRequest(101L,List.of(new OrderItemRequest(10L,2))));
        verify(product).reserve(argThat(r->r.orderId().equals(7L)&&r.items().getFirst().quantity().equals(2)));
        verify(orders,times(2)).saveAndFlush(any(Order.class));
    }
    @Test void createOrder_duplicateProductItems_rejected() {
        assertThatThrownBy(()->service.createOrder(new CreateOrderRequest(1L,List.of(new OrderItemRequest(1L,1),new OrderItemRequest(1L,2)))))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(product,orders);
    }
}
