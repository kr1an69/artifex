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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        if (request.getUsername() == null || request.getUsername().trim().isEmpty() ||
            request.getEmail() == null || request.getEmail().trim().isEmpty() ||
            request.getPassword() == null || request.getPassword().trim().isEmpty() ||
            request.getFullName() == null || request.getFullName().trim().isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập đầy đủ thông tin bắt buộc!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (userRepository.existsByUsername(request.getUsername().trim())) {
            response.put("status", "ERROR");
            response.put("message", "Tên đăng nhập đã tồn tại!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (userRepository.existsByEmail(request.getEmail().trim())) {
            response.put("status", "ERROR");
            response.put("message", "Email đã được sử dụng!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        User newUser = User.builder()
                .username(request.getUsername().trim())
                .email(request.getEmail().trim().toLowerCase())
                .password(request.getPassword())
                .fullName(request.getFullName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .avatarUrl("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80")
                .role("USER")
                .build();

        User savedUser = userRepository.save(newUser);
        savedUser.setPassword(null);

        response.put("status", "SUCCESS");
        response.put("message", "Đăng ký tài khoản thành công!");
        response.put("user", savedUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        if (request.getUsernameOrEmail() == null || request.getPassword() == null) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập đầy đủ thông tin đăng nhập!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String input = request.getUsernameOrEmail().trim();
        Optional<User> userOpt = userRepository.findByUsername(input);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(input.toLowerCase());
        }

        if (userOpt.isEmpty() || !userOpt.get().getPassword().equals(request.getPassword())) {
            response.put("status", "ERROR");
            response.put("message", "Tài khoản hoặc mật khẩu không chính xác!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        User user = userOpt.get();
        user.setPassword(null);

        response.put("status", "SUCCESS");
        response.put("message", "Đăng nhập thành công!");
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/firebase")
    public ResponseEntity<Map<String, Object>> firebaseLogin(@RequestBody FirebaseLoginRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Email từ Firebase không hợp lệ!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String email = request.getEmail().trim().toLowerCase();
        Optional<User> existingUserOpt = userRepository.findByEmail(email);

        User user;
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            boolean updated = false;
            if (request.getFullName() != null && !request.getFullName().trim().isEmpty()
                    && (user.getFullName() == null || user.getFullName().trim().isEmpty())) {
                user.setFullName(request.getFullName().trim());
                updated = true;
            }
            if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()
                    && (user.getAvatarUrl() == null || user.getAvatarUrl().trim().isEmpty())) {
                user.setAvatarUrl(request.getAvatarUrl().trim());
                updated = true;
            }
            if (updated) {
                user = userRepository.save(user);
            }
        } else {
            String genUsername = email.split("@")[0] + "_" + (System.currentTimeMillis() % 10000);
            String fullName = (request.getFullName() != null && !request.getFullName().trim().isEmpty())
                    ? request.getFullName().trim() : email.split("@")[0];
            String avatar = (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty())
                    ? request.getAvatarUrl().trim() : "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80";

            User newUser = User.builder()
                    .username(genUsername)
                    .email(email)
                    .password("FIREBASE_AUTH")
                    .fullName(fullName)
                    .avatarUrl(avatar)
                    .role("USER")
                    .build();

            user = userRepository.save(newUser);
        }

        user.setPassword(null);
        response.put("status", "SUCCESS");
        response.put("message", "Đăng nhập Firebase thành công!");
        response.put("firebaseUid", request.getFirebaseUid());
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        String identifier = request.getUsernameOrEmail() != null ? request.getUsernameOrEmail().trim() : "";
        String confirmEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String newPassword = request.getNewPassword() != null ? request.getNewPassword().trim() : "";

        if (identifier.isEmpty() && confirmEmail.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập tên đăng nhập hoặc địa chỉ email!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (newPassword.length() < 6) {
            response.put("status", "ERROR");
            response.put("message", "Mật khẩu mới phải có ít nhất 6 ký tự!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<User> userOpt = Optional.empty();
        if (!identifier.isEmpty()) {
            userOpt = userRepository.findByUsername(identifier);
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail(identifier.toLowerCase());
            }
        } else {
            userOpt = userRepository.findByEmail(confirmEmail);
        }

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy tài khoản khớp với thông tin đã nhập!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        if (!confirmEmail.isEmpty() && !confirmEmail.equalsIgnoreCase(user.getEmail())) {
            response.put("status", "ERROR");
            response.put("message", "Email xác nhận không khớp với tài khoản này!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        user.setPassword(newPassword);
        userRepository.save(user);

        response.put("status", "SUCCESS");
        response.put("message", "Đặt lại mật khẩu thành công!");
        return ResponseEntity.ok(response);
    }
}
