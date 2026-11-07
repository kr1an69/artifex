package com.artifex.backend.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

public final class PasswordUtils {

    public static final String FIREBASE_PLACEHOLDER = "FIREBASE_AUTH";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtils() {
    }

    public static boolean containsWhitespace(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Kiểm tra mật khẩu hợp lệ (KHÔNG trim):
     * - Không rỗng
     * - Tuyệt đối không chứa khoảng trắng ở bất kỳ vị trí nào (đầu, giữa, cuối)
     * - Tối thiểu 6 ký tự
     * - Tối đa 72 bytes theo giới hạn chuẩn của thuật toán BCrypt
     */
    public static String validateRawPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return "Vui lòng nhập mật khẩu!";
        }
        if (containsWhitespace(rawPassword)) {
            return "Mật khẩu không được chứa khoảng trắng!";
        }
        if (rawPassword.length() < 6) {
            return "Mật khẩu phải có ít nhất 6 ký tự!";
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Mật khẩu không được vượt quá 72 ký tự!";
        }
        return null;
    }

    public static boolean isFirebaseOnlyAccount(String storedPassword) {
        return FIREBASE_PLACEHOLDER.equals(storedPassword);
    }

    public static boolean isBcryptHash(String storedPassword) {
        return storedPassword != null
                && storedPassword.length() == 60
                && (storedPassword.startsWith("$2a$")
                || storedPassword.startsWith("$2b$")
                || storedPassword.startsWith("$2y$"));
    }

    public static String hash(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * Đối chiếu mật khẩu đăng nhập / mật khẩu hiện tại:
     * - Chặn cứng tài khoản chỉ đăng nhập qua Google ("FIREBASE_AUTH").
     * - Nếu DB đã lưu BCrypt hash -> kiểm tra bằng BCryptPasswordEncoder.
     * - Tương thích ngược với tài khoản plaintext mẫu trong seed_data.sql ('123456').
     */
    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null || isFirebaseOnlyAccount(storedPassword)) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            return ENCODER.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }
}
