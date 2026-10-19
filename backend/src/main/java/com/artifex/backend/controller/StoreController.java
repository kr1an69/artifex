package com.artifex.backend.controller;

import com.artifex.backend.entity.Store;
import com.artifex.backend.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// @RestController = @Controller + @ResponseBody:
// Báo cho Spring Boot biết class này là nơi tiếp nhận HTTP Request từ Client (Android/Postman)
// và tự động dùng thư viện Jackson biến kết quả Java Object trả về thành chuỗi JSON.
@RestController

// Định nghĩa tiền tố URL (Base Path) chung cho toàn bộ API trong class này là:
// http://localhost:8080/api/stores
@RequestMapping("/api/stores")

// Cơ chế Dependency Injection (Tiêm phụ thuộc) qua Constructor của Lombok:
// Tự động khởi tạo và nạp các biến "final" (như storeRepository) mà không cần
// viết từ khóa "new" thủ công.
@RequiredArgsConstructor

// Mở quyền CORS (Cross-Origin Resource Sharing):
// Cho phép các client khác domain/port (như máy ảo Android, trình duyệt) gọi
// vào API mà không bị chặn bảo mật.
@CrossOrigin(origins = "*")
public class StoreController {
    // controller for finding stores google map

    // StoreRepository kế thừa JpaRepository, đóng vai trò cầu nối trực tiếp xuống
    // bảng "stores" trong MySQL
    private final StoreRepository storeRepository;

    // @GetMapping không có tham số đường dẫn phụ -> ánh xạ thẳng vào GET
    // /api/stores.
    // Khi Android gọi Retrofit GET("api/stores"), hàm này sẽ được kích hoạt.
    @GetMapping
    public ResponseEntity<List<Store>> getAllStores() {
        // storeRepository.findAll() là hàm có sẵn của Spring Data JPA,
        // tự động sinh câu lệnh SQL: "SELECT * FROM stores" và map từng dòng dữ liệu
        // thành object Store.
        List<Store> stores = storeRepository.findAll();

        // ResponseEntity là lớp bọc chuẩn của Spring để trả về cho Client cả 2 thứ:
        // 1. HTTP Status Code: .ok() tương ứng mã 200 OK (gọi API thành công)
        // 2. HTTP Body: danh sách "stores" được tự động serialize thành mảng JSON [...]
        return ResponseEntity.ok(stores);
    }
}
