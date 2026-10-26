package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.*;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        Map<String, Object> response = new LinkedHashMap<>();
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        user.setPassword(null);

        response.put("status", "SUCCESS");
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProfile(@PathVariable Long id,
                                                            @RequestBody UpdateProfileRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress().trim());
        }

        User updatedUser = userRepository.save(user);
        updatedUser.setPassword(null);

        response.put("status", "SUCCESS");
        response.put("message", "Cập nhật thông tin thành công!");
        response.put("user", updatedUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/avatar")
    public ResponseEntity<Map<String, Object>> updateAvatar(@PathVariable Long id,
                                                           @RequestBody UpdateAvatarRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        if (request.getAvatarUrl() == null || request.getAvatarUrl().trim().isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Đường dẫn avatar không hợp lệ!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        User user = userOpt.get();
        user.setAvatarUrl(request.getAvatarUrl().trim());

        User updatedUser = userRepository.save(user);
        updatedUser.setPassword(null);

        response.put("status", "SUCCESS");
        response.put("message", "Cập nhật ảnh đại diện thành công!");
        response.put("user", updatedUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, Object>> changePassword(@PathVariable Long id,
                                                              @RequestBody ChangePasswordRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Kiểm tra chặn nếu là tài khoản đăng nhập qua Google
        if ("FIREBASE_AUTH".equals(user.getPassword())) {
            response.put("status", "ERROR");
            response.put("message", "Tài khoản liên kết Google không sử dụng mật khẩu này!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Validate dữ liệu đầu vào
        if (request.getOldPassword() == null || request.getNewPassword() == null ||
            request.getNewPassword().trim().length() < 6) {
            response.put("status", "ERROR");
            response.put("message", "Mật khẩu mới phải có ít nhất 6 ký tự!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Kiểm tra khớp mật khẩu cũ
        if (!user.getPassword().equals(request.getOldPassword().trim())) {
            response.put("status", "ERROR");
            response.put("message", "Mật khẩu hiện tại không chính xác!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        user.setPassword(request.getNewPassword().trim());
        userRepository.save(user);

        response.put("status", "SUCCESS");
        response.put("message", "Đổi mật khẩu thành công!");
        return ResponseEntity.ok(response);
    }
}
