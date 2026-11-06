package com.artifex.backend.service;

import com.artifex.backend.entity.User;
import com.artifex.backend.repository.UserRepository;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FirebaseLoginService {
    private final UserRepository users;
    private final FirebaseTokenVerifier verifier;

    @Transactional
    public Map<String, Object> login(String idToken) {
        FirebaseToken token = verifier.verify(idToken);
        String uid = token.getUid();
        if (uid == null || uid.isBlank() || uid.length() > 128
                || !token.isEmailVerified() || token.getEmail() == null || token.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Tài khoản Firebase phải có UID hợp lệ và email đã xác minh.");
        }
        String email = token.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = users.findByFirebaseUid(uid).orElse(null);
        // Also fail closed if an existing database still uses case-insensitive collation.
        if (user != null && !uid.equals(user.getFirebaseUid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Liên kết Firebase UID không khớp.");
        }
        if (user == null) {
            // Local emails were never verified. Email alone must not establish ownership.
            if (users.existsByEmail(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Email đã thuộc tài khoản khác. Vui lòng dùng cách đăng nhập cũ; chưa hỗ trợ tự động liên kết tài khoản.");
            }
            if (email.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email vượt quá độ dài cho phép.");
            }
            String name = token.getName();
            if (name == null || name.isBlank()) name = email.split("@")[0];
            String picture = token.getPicture();
            user = users.saveAndFlush(User.builder()
                    .firebaseUid(uid).username("fb_" + UUID.randomUUID())
                    .email(email).password("FIREBASE_AUTH")
                    .fullName(name.substring(0, Math.min(name.length(), 100)))
                    .avatarUrl(picture != null && picture.length() <= 500 ? picture : null)
                    .role("USER").build());
        }
        // Do not mutate a managed entity's password to serialize a public response.
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("fullName", user.getFullName());
        profile.put("phone", user.getPhone());
        profile.put("address", user.getAddress());
        profile.put("dateOfBirth", user.getDateOfBirth());
        profile.put("gender", user.getGender());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("role", user.getRole());
        return Map.of("status", "SUCCESS", "message", "Đăng nhập Firebase thành công!", "user", profile);
    }
}
