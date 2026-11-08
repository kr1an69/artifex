package com.artifex.backend.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service quản lý Session Token để xác thực danh tính và chống lỗ hổng IDOR.
 */
@Service
public class TokenService {

    // Token có thời hạn 7 ngày
    private static final long EXPIRATION_TIME_MS = 7L * 24 * 60 * 60 * 1000L;
    private final Map<String, TokenSession> tokenStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public static class TokenSession {
        private final Long userId;
        private final String role;
        private final long expiresAt;

        public TokenSession(Long userId, String role, long expiresAt) {
            this.userId = userId;
            this.role = role != null ? role : "USER";
            this.expiresAt = expiresAt;
        }

        public Long getUserId() {
            return userId;
        }

        public String getRole() {
            return role;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Sinh token ngẫu nhiên bảo mật 256-bit (32 bytes) và lưu session vào bộ nhớ server.
     */
    public String generateToken(Long userId, String role) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = "artifex_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        tokenStore.put(token, new TokenSession(userId, role, System.currentTimeMillis() + EXPIRATION_TIME_MS));
        return token;
    }

    /**
     * Kiểm tra chuỗi Authorization header (Bearer <token>).
     * Trả về TokenSession nếu hợp lệ, ngược lại trả về null.
     */
    public TokenSession validateHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        TokenSession session = tokenStore.get(token);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            tokenStore.remove(token);
            return null;
        }
        return session;
    }

    /**
     * Thu hồi / Hủy token khi người dùng đăng xuất.
     */
    public void revokeHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            tokenStore.remove(token);
        }
    }
}
