package com.artifex.backend.controller;

import com.artifex.backend.entity.Order;
import com.artifex.backend.entity.OrderDetail;
import com.artifex.backend.repository.OrderDetailRepository;
import com.artifex.backend.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Khai báo lớp này là REST Controller, mọi hàm sẽ trả về dữ liệu dạng JSON thay vì giao diện HTML
@RestController
// Thiết lập đường dẫn gốc (Base URL) cho các API lịch sử đơn hàng là
// /api/orders
@RequestMapping("/api/orders")
// Yêu cầu Lombok tự sinh constructor cho các thuộc tính final (Dependency
// Injection)
@RequiredArgsConstructor
// Cho phép mọi nguồn (client Android, Postman) truy cập API mà không bị lỗi
// chặn CORS
@CrossOrigin(origins = "*")
public class OrderHistoryController {
    // OrderRepository để thao tác với table orders DB
    private final OrderRepository orderRepository;
    // OrderDetailRepository để get list sp của từng order
    private final OrderDetailRepository orderDetailRepository;

    // Đăng ký API HTTP GET tại đường dẫn: /api/orders/user/{userId}
    // @PathVariable giúp trích xuất giá trị {userId} nằm trên URL truyền thẳng vào
    // biến Long userId của hàm
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Map<String, Object>>> getOrderHistoryByUser(@PathVariable Long userId) {

        // Spring Data JPA Query Method: tự động dịch tên hàm thành câu SQL:
        // SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC (đơn mới nhất
        // lên đầu)
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Khởi tạo danh sách kết quả cuối cùng sẽ được Jackson chuyển thành mảng JSON
        // trả về cho Android
        List<Map<String, Object>> responseList = new ArrayList<>();

        for (Order order : orders) {
            // Truy vấn bảng order_details để lấy toàn bộ các món linh kiện thuộc về đơn
            // hàng hiện tại
            List<OrderDetail> details = orderDetailRepository.findByOrderId(order.getId());

            // Lý do phải đóng gói lại qua Map thay vì trả thẳng List<OrderDetail>:
            // Trong Entity OrderDetail, thuộc tính "order" được cấu hình là
            // @ManyToOne(fetch = FetchType.LAZY).
            // Nếu trả trực tiếp OrderDetail ra JSON, thư viện Jackson sẽ cố đọc đối tượng
            // proxy LAZY của Hibernate
            // dẫn đến lỗi crash 500 (ByteBuddyInterceptor) hoặc bị lặp JSON dư thừa.
            List<Map<String, Object>> itemList = new ArrayList<>();

            for (OrderDetail detail : details) {
                // LinkedHashMap giúp tạo cấu trúc JSON (key-value) và giữ nguyên thứ tự các
                // trường như lúc put vào
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("id", detail.getId());
                itemMap.put("product", detail.getProduct()); // Chứa toàn bộ object Product (tên, ảnh, giá...) vì
                                                             // Product được load EAGER
                itemMap.put("quantity", detail.getQuantity());
                itemMap.put("unitPrice", detail.getUnitPrice());
                itemList.add(itemMap);
            }

            // Đóng gói thông tin chung của đơn hàng kèm danh sách món ("items") thành 1
            // JSON Object hoàn chỉnh
            Map<String, Object> orderMap = new LinkedHashMap<>();
            orderMap.put("id", order.getId());
            orderMap.put("userId", order.getUser().getId());
            orderMap.put("totalPrice", order.getTotalPrice());
            orderMap.put("status", order.getStatus()); // order sts (PENDING, SHIPPING, COMPLETED, CANCELLED)
            orderMap.put("receiverName", order.getReceiverName());
            orderMap.put("receiverPhone", order.getReceiverPhone());
            orderMap.put("shippingAddress", order.getShippingAddress());
            orderMap.put("createdAt", order.getCreatedAt());
            orderMap.put("items", itemList);

            responseList.add(orderMap);
        }

        // Trả về HTTP 200 OK cùng danh sách lịch sử đơn hàng dạng JSON trong phần Body
        return ResponseEntity.ok(responseList);
    }

    // Đăng ký API HTTP PUT tại đường dẫn: /api/orders/{orderId}/cancel
    // Trong chuẩn RESTful API, method PUT được dùng khi muốn cập nhật trạng thái/dữ
    // liệu đã tồn tại trên Server
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable Long orderId) {
        // Tìm kiếm đơn hàng trong MySQL theo khóa chính (Primary Key).
        // Kiểu Optional<Order> giúp bọc kết quả lại để tránh lỗi NullPointerException
        // nếu ID không tồn tại.
        Optional<Order> optionalOrder = orderRepository.findById(orderId);

        Map<String, Object> response = new LinkedHashMap<>();

        // Nếu ID đơn hàng truyền lên không có trong DB -> trả về mã lỗi chuẩn HTTP 404
        // (Not Found)
        if (optionalOrder.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy đơn hàng với ID: " + orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        // Lấy đối tượng Entity Order thực tế ra khỏi vỏ bọc Optional
        Order order = optionalOrder.get();

        // Kiểm tra quy tắc nghiệp vụ (Business Logic): chỉ cho phép hủy khi đơn đang
        // chờ xác nhận (PENDING).
        // Nếu đơn đang giao (SHIPPING) hoặc đã xong (COMPLETED) -> trả về mã lỗi HTTP
        // 400 (Bad Request)
        if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
            response.put("status", "ERROR");
            response.put("message",
                    "Chỉ có thể hủy đơn hàng đang ở trạng thái PENDING. Trạng thái hiện tại: " + order.getStatus());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Đổi trạng thái trên object Java
        order.setStatus("CANCELLED");

        // save changes xuống db (same lệnh UPDATE orders SET status = 'CANCELLED' WHERE
        // id = ?)
        // Vì đối tượng "order" đã có sẵn id, hàm save() của JPA sẽ tự hiểu là lệnh
        // UPDATE chứ không phải INSERT
        Order updatedOrder = orderRepository.save(order);

        response.put("status", "SUCCESS");
        response.put("message", "Hủy đơn hàng thành công!");
        response.put("order", updatedOrder);
        return ResponseEntity.ok(response);
    }
}
