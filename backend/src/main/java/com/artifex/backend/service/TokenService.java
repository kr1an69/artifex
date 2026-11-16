package com.artifex.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service quản lý Session Token:
 * - Sử dụng chữ ký số mật mã HMAC-SHA256 (Stateless Token) để duy trì phiên đăng nhập bền vững
 *   ngay cả khi khởi động lại (restart) máy chủ Spring Boot.
 * - Hỗ trợ thu hồi (revoke) từng token khi đăng xuất và vô hiệu toàn bộ token cũ theo userId
 *   khi người dùng đổi mật khẩu hoặc đặt lại mật khẩu.
 * - Ở môi trường production (profile "prod"), bắt buộc cấu hình ARTIFEX_JWT_SECRET riêng.
 */
@Service
public class TokenService {

    // Token có thời hạn 7 ngày
    private static final long EXPIRATION_TIME_MS = 7L * 24 * 60 * 60 * 1000L;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_PREFIX = "artifex.";
    static final String DEV_FALLBACK_SECRET = "ArtifexSecretKeyForTokenSigning2026Secure256BitKey!";

    private final byte[] secretKeyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    // Danh sách các token đã bị thu hồi khi đăng xuất
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    // Mốc thời gian (epoch ms) vô hiệu hóa toàn bộ token cấp trước đó của từng user
    private final Map<Long, Long> userInvalidBefore = new ConcurrentHashMap<>();

    // Tương thích ngược với các token ngẫu nhiên cũ
    private final Map<String, TokenSession> legacyTokenStore = new ConcurrentHashMap<>();

    public TokenService() {
        this(DEV_FALLBACK_SECRET);
    }

    public TokenService(String secretKey) {
        this(secretKey, false);
    }

    @Autowired
    public TokenService(@Value("${artifex.jwt.secret:}") String secretKey, Environment environment) {
        this(secretKey, isProdEnvironment(environment));
    }

    TokenService(String secretKey, boolean prodMode) {
        String trimmed = secretKey != null ? secretKey.trim() : "";
        if (prodMode && (trimmed.isEmpty() || DEV_FALLBACK_SECRET.equals(trimmed))) {
            throw new IllegalStateException(
                    "Môi trường production yêu cầu cấu hình biến môi trường ARTIFEX_JWT_SECRET riêng (không được dùng khóa mặc định)."
            );
        }
        String effectiveSecret;
        if (trimmed.isEmpty()) {
            effectiveSecret = DEV_FALLBACK_SECRET;
            System.err.println("[SECURITY WARNING] ARTIFEX_JWT_SECRET chưa được thiết lập; đang dùng secret phát triển nội bộ.");
        } else {
            effectiveSecret = trimmed;
        }
        this.secretKeyBytes = effectiveSecret.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isProdEnvironment(Environment environment) {
        if (environment == null) {
            return false;
        }
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }

    public static class TokenSession {
        private final Long userId;
        private final String role;
        private final long expiresAt;
        private final long issuedAt;

        public TokenSession(Long userId, String role, long expiresAt) {
            this(userId, role, expiresAt, expiresAt - EXPIRATION_TIME_MS);
        }

        public TokenSession(Long userId, String role, long expiresAt, long issuedAt) {
            this.userId = userId;
            this.role = role != null ? role : "USER";
            this.expiresAt = expiresAt;
            this.issuedAt = issuedAt;
        }

        public Long getUserId() {
            return userId;
        }

        public String getRole() {
            return role;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public long getIssuedAt() {
            return issuedAt;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Sinh token HMAC-SHA256 bền vững:
     * Định dạng: artifex.<Base64URL(payload)>.<Base64URL(chữ ký HMAC-SHA256)>
     */
    public String generateToken(Long userId, String role) {
        long now = System.currentTimeMillis();
        Long invalidBefore = userId != null ? userInvalidBefore.get(userId) : null;
        long issuedAt = invalidBefore != null ? Math.max(now, invalidBefore + 1L) : now;
        long expiresAt = issuedAt + EXPIRATION_TIME_MS;
        String safeRole = (role != null && !role.isBlank()) ? role.trim() : "USER";
        byte[] nonceBytes = new byte[8];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        String payload = userId + ":" + safeRole + ":" + expiresAt + ":" + nonce + ":" + issuedAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = computeHmac(encodedPayload);

        return TOKEN_PREFIX + encodedPayload + "." + signature;
    }

    /**
     * Vô hiệu hóa toàn bộ token đã cấp trước thời điểm hiện tại cho userId (khi đổi/reset mật khẩu).
     */
    public long invalidateUserTokens(Long userId) {
        return invalidateUserTokens(userId, System.currentTimeMillis());
    }

    public long invalidateUserTokens(Long userId, long cutoffMillis) {
        if (userId != null) {
            return userInvalidBefore.merge(userId, cutoffMillis, Math::max);
        }
        return cutoffMillis;
    }

    /**
     * Kiểm tra chuỗi Authorization header (Bearer <token>).
     * Trả về TokenSession nếu chữ ký hợp lệ, chưa hết hạn và chưa bị thu hồi; ngược lại trả về null.
     */
    public TokenSession validateHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty() || revokedTokens.contains(token)) {
            return null;
        }

        // 1. Kiểm tra Token HMAC-SHA256 chuẩn
        if (token.startsWith(TOKEN_PREFIX)) {
            String tokenBody = token.substring(TOKEN_PREFIX.length());
            int dotIndex = tokenBody.indexOf('.');
            if (dotIndex <= 0 || dotIndex >= tokenBody.length() - 1) {
                return null;
            }

            String encodedPayload = tokenBody.substring(0, dotIndex);
            String signature = tokenBody.substring(dotIndex + 1);

            String expectedSignature = computeHmac(encodedPayload);
            if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            try {
                byte[] decodedPayloadBytes = Base64.getUrlDecoder().decode(encodedPayload);
                String payload = new String(decodedPayloadBytes, StandardCharsets.UTF_8);
                String[] parts = payload.split(":");
                if (parts.length < 3) {
                    return null;
                }
                Long userId = Long.parseLong(parts[0]);
                String role = parts[1];
                long expiresAt = Long.parseLong(parts[2]);
                long issuedAt = parts.length >= 5
                        ? Long.parseLong(parts[4])
                        : (expiresAt - EXPIRATION_TIME_MS);

                if (System.currentTimeMillis() > expiresAt) {
                    revokedTokens.add(token);
                    return null;
                }

                Long invalidBefore = userInvalidBefore.get(userId);
                if (invalidBefore != null && issuedAt <= invalidBefore) {
                    return null;
                }

                return new TokenSession(userId, role, expiresAt, issuedAt);
            } catch (Exception e) {
                return null;
            }
        }

        // 2. Tương thích ngược với định dạng cũ (nếu có trong phiên chạy hiện tại)
        TokenSession legacySession = legacyTokenStore.get(token);
        if (legacySession != null) {
            if (legacySession.isExpired()) {
                legacyTokenStore.remove(token);
                return null;
            }
            Long invalidBefore = userInvalidBefore.get(legacySession.getUserId());
            if (invalidBefore != null && legacySession.getIssuedAt() <= invalidBefore) {
                legacyTokenStore.remove(token);
                return null;
            }
            return legacySession;
        }

        return null;
    }

    /**
     * Thu hồi / Hủy token khi người dùng đăng xuất.
     */
    public void revokeHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                revokedTokens.add(token);
                legacyTokenStore.remove(token);
            }
        }
    }

    private String computeHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi khởi tạo thuật toán mã hóa HMAC-SHA256: " + e.getMessage(), e);
        }
    }
}
