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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Xử lý Đặt hàng (Checkout) - Phụ trách: Hiếu.
 * Cùng base path /api/orders với OrderHistoryController nhưng khác method/đường dẫn:
 *  - POST /api/orders       : tạo đơn hàng mới
 *  - GET  /api/orders/{id}  : xem chi tiết 1 đơn hàng
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Tạo đơn hàng: kiểm tra user, kiểm tra tồn kho, tính tổng tiền theo giá trong DB,
     * trừ kho và lưu Order + OrderDetail trong cùng 1 transaction (lỗi giữa chừng sẽ rollback hết).
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        Optional<User> optionalUser = userRepository.findById(request.getUserId());
        if (optionalUser.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng với ID: " + request.getUserId());
        }

        // Bước 1: kiểm tra từng sản phẩm và tồn kho, tính tổng tiền
        List<Product> products = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderCreateRequest.Item item : request.getItems()) {
            Optional<Product> optionalProduct = productRepository.findById(item.getProductId());
            if (optionalProduct.isEmpty()) {
                return error(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm với ID: " + item.getProductId());
            }
            Product product = optionalProduct.get();
            int stock = product.getStock() != null ? product.getStock() : 0;
            if (stock < item.getQuantity()) {
                return error(HttpStatus.BAD_REQUEST,
                        "Sản phẩm \"" + product.getName() + "\" chỉ còn " + stock + " trong kho");
            }
            products.add(product);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        // Bước 2: lưu đơn hàng
        Order order = Order.builder()
                .user(optionalUser.get())
                .totalPrice(total)
                .status("PENDING")
                .receiverName(request.getReceiverName().trim())
                .receiverPhone(request.getReceiverPhone().trim())
                .shippingAddress(request.getShippingAddress().trim())
                .build();
        order = orderRepository.save(order);

        // Bước 3: lưu chi tiết đơn và trừ kho
        for (int i = 0; i < request.getItems().size(); i++) {
            OrderCreateRequest.Item item = request.getItems().get(i);
            Product product = products.get(i);

            orderDetailRepository.save(OrderDetail.builder()
                    .order(order)
                    .product(product)
                    .quantity(item.getQuantity())
                    .unitPrice(product.getPrice())
                    .build());

            product.setStock(product.getStock() - item.getQuantity());
            productRepository.save(product);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Đặt hàng thành công!");
        response.put("order", toOrderMap(order));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Xem chi tiết 1 đơn hàng theo id. */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrderById(@PathVariable Long id) {
        Optional<Order> optionalOrder = orderRepository.findById(id);
        if (optionalOrder.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng với ID: " + id);
        }
        return ResponseEntity.ok(toOrderMap(optionalOrder.get()));
    }

    // Đóng gói qua Map (giống OrderHistoryController) để tránh lỗi serialize proxy LAZY của Hibernate
    private Map<String, Object> toOrderMap(Order order) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (OrderDetail detail : orderDetailRepository.findByOrderId(order.getId())) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("id", detail.getId());
            itemMap.put("product", detail.getProduct());
            itemMap.put("quantity", detail.getQuantity());
            itemMap.put("unitPrice", detail.getUnitPrice());
            items.add(itemMap);
        }

        Map<String, Object> orderMap = new LinkedHashMap<>();
        orderMap.put("id", order.getId());
        orderMap.put("userId", order.getUser().getId());
        orderMap.put("totalPrice", order.getTotalPrice());
        orderMap.put("status", order.getStatus());
        orderMap.put("receiverName", order.getReceiverName());
        orderMap.put("receiverPhone", order.getReceiverPhone());
        orderMap.put("shippingAddress", order.getShippingAddress());
        orderMap.put("createdAt", order.getCreatedAt());
        orderMap.put("items", items);
        return orderMap;
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
