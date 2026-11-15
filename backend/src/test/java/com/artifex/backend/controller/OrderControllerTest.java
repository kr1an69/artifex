package com.artifex.backend.controller;

import com.artifex.backend.dto.request.OrderCreateRequest;
import com.artifex.backend.entity.Order;
import com.artifex.backend.entity.OrderDetail;
import com.artifex.backend.entity.Product;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.OrderDetailRepository;
import com.artifex.backend.repository.OrderRepository;
import com.artifex.backend.repository.ProductRepository;
import com.artifex.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho OrderController (Checkout - Phụ trách: Hiếu).
 */
class OrderControllerTest {

    private OrderRepository orderRepository;
    private OrderDetailRepository orderDetailRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private OrderController orderController;

    @BeforeEach
    void setup() {
        orderRepository = mock(OrderRepository.class);
        orderDetailRepository = mock(OrderDetailRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        orderController = new OrderController(
                orderRepository, orderDetailRepository, productRepository, userRepository
        );
    }

    @Test
    void createOrderCalculatesServerTotalAndDeductsStock() {
        User user = User.builder().id(1L).username("nguyenvana").build();
        Product cpu = Product.builder()
                .id(1L)
                .name("Intel Core i5-13600K")
                .price(new BigDecimal("7890000"))
                .stock(10)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(1L)).thenReturn(Optional.of(cpu));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(orderDetailRepository.findByOrderId(100L)).thenReturn(Collections.emptyList());

        OrderCreateRequest.Item item = new OrderCreateRequest.Item();
        item.setProductId(1L);
        item.setQuantity(2);

        OrderCreateRequest request = new OrderCreateRequest();
        request.setUserId(1L);
        request.setReceiverName("Nguyễn Văn A");
        request.setReceiverPhone("0901234567");
        request.setShippingAddress("TP. Thủ Đức, TP.HCM");
        request.setItems(List.of(item));

        ResponseEntity<Map<String, Object>> response = orderController.createOrder(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> orderMap = (Map<String, Object>) response.getBody().get("order");
        assertEquals(new BigDecimal("15780000"), orderMap.get("totalPrice"));
        assertEquals("PENDING", orderMap.get("status"));
        assertEquals(8, cpu.getStock());

        verify(orderRepository).save(any(Order.class));
        verify(orderDetailRepository).save(any(OrderDetail.class));
        verify(productRepository).save(cpu);
    }

    @Test
    void createOrderRejectsWhenStockInsufficient() {
        User user = User.builder().id(1L).username("nguyenvana").build();
        Product gpu = Product.builder()
                .id(4L)
                .name("RTX 4070 Super")
                .price(new BigDecimal("16990000"))
                .stock(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(4L)).thenReturn(Optional.of(gpu));

        OrderCreateRequest.Item item = new OrderCreateRequest.Item();
        item.setProductId(4L);
        item.setQuantity(3);

        OrderCreateRequest request = new OrderCreateRequest();
        request.setUserId(1L);
        request.setReceiverName("Nguyễn Văn A");
        request.setReceiverPhone("0901234567");
        request.setShippingAddress("TP. Thủ Đức, TP.HCM");
        request.setItems(List.of(item));

        ResponseEntity<Map<String, Object>> response = orderController.createOrder(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ERROR", response.getBody().get("status"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrderByIdReturnsNotFoundWhenMissing() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = orderController.getOrderById(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ERROR", response.getBody().get("status"));
    }
}
