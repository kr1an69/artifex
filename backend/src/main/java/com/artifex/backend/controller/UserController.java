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
        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.endsWith("@gmail.com")) {
                response.put("status", "ERROR");
                response.put("message", "Email phải có định dạng @gmail.com!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (!newEmail.equals(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                response.put("status", "ERROR");
                response.put("message", "Email đã được sử dụng bởi tài khoản khác!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            user.setEmail(newEmail);
        }
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            String rawPhone = request.getPhone().trim();
            if (!isPhoneNumber(rawPhone)) {
                response.put("status", "ERROR");
                response.put("message", "Số điện thoại không hợp lệ! Vui lòng nhập đúng 10 số (ví dụ: 09xxxxxxxx).");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            String domesticPhone = toDomesticVietnamPhone(rawPhone);
            String targetNorm = normalizeVietnamPhone(rawPhone);
            boolean phoneTaken = userRepository.findAll().stream()
                    .anyMatch(u -> !u.getId().equals(id) && u.getPhone() != null && normalizeVietnamPhone(u.getPhone()).equals(targetNorm));
            if (phoneTaken) {
                response.put("status", "ERROR");
                response.put("message", "Số điện thoại này đã được sử dụng bởi tài khoản khác!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            user.setPhone(domesticPhone);
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress().trim());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth().trim());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender().trim());
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

    // ==================== HÀM HỖ TRỢ XỬ LÝ SỐ ĐIỆN THOẠI VIỆT NAM ====================

    private boolean isPhoneNumber(String input) {
        if (input == null) return false;
        String cleaned = input.replaceAll("[\\s\\-\\.\\(\\)]", "");
        return cleaned.matches("^(\\+?840?|0)[35789]\\d{8}$");
    }

    private String normalizeVietnamPhone(String rawPhone) {
        if (rawPhone == null) return "";
        String cleaned = rawPhone.replaceAll("[^0-9+]", "");
        if (cleaned.startsWith("+840")) {
            return "+84" + cleaned.substring(4);
        }
        if (cleaned.startsWith("840") && cleaned.length() == 12) {
            return "+84" + cleaned.substring(3);
        }
        if (cleaned.startsWith("0")) {
            return "+84" + cleaned.substring(1);
        }
        if (cleaned.startsWith("84")) {
            return "+" + cleaned;
        }
        if (!cleaned.startsWith("+") && !cleaned.isEmpty()) {
            return "+" + cleaned;
        }
        return cleaned;
    }

    private String toDomesticVietnamPhone(String rawPhone) {
        if (rawPhone == null) return "";
        String e164 = normalizeVietnamPhone(rawPhone);
        if (e164.startsWith("+84")) {
            return "0" + e164.substring(3);
        }
        return rawPhone.replaceAll("[^0-9]", "");
    }
}
