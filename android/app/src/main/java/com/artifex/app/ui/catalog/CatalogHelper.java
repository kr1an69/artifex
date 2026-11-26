package com.artifex.app.ui.catalog;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Bộ hàm hỗ trợ nghiệp vụ độc lập cho module Catalog (Đạt - Bạn 2):
 * - Giải mã và kiểm tra vai trò ADMIN từ session token HMAC (không sửa SharedPrefManager của Auth).
 * - Validate dữ liệu tạo sản phẩm khớp với ProductCreateRequest trên Backend.
 * - Trích xuất thông báo lỗi từ HTTP error body.
 */
public final class CatalogHelper {

    private static final String TOKEN_PREFIX = "artifex.";
    private static final long MAX_PRODUCT_PRICE = 9_999_999_999L; // Khớp DECIMAL(12,2) trong MySQL
    private static final int MAX_PRODUCT_NAME_LENGTH = 150;

    private CatalogHelper() {
    }

    /**
     * Kiểm tra session token có thuộc tài khoản ADMIN còn hạn sử dụng hay không.
     * Định dạng payload token do TokenService sinh ra: userId:role:expiresAt:nonce
     */
    public static boolean isAdminToken(String token, long nowMillis) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            return false;
        }
        String tokenBody = token.substring(TOKEN_PREFIX.length());
        int dotIndex = tokenBody.indexOf('.');
        if (dotIndex <= 0) {
            return false;
        }
        String encodedPayload = tokenBody.substring(0, dotIndex);
        String decodedPayload = decodeBase64UrlToUtf8(encodedPayload);
        if (decodedPayload == null || decodedPayload.isEmpty()) {
            return false;
        }

        String[] parts = decodedPayload.split(":");
        if (parts.length < 3) {
            return false;
        }

        String role = parts[1].trim();
        try {
            long expiresAt = Long.parseLong(parts[2].trim());
            if (expiresAt <= nowMillis) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return "ADMIN".equalsIgnoreCase(role);
    }

    public static String validateProductName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Vui lòng nhập tên linh kiện";
        }
        if (name.trim().length() > MAX_PRODUCT_NAME_LENGTH) {
            return "Tên linh kiện không được vượt quá 150 ký tự";
        }
        return null;
    }

    public static String validateProductPrice(String priceText) {
        if (priceText == null || priceText.trim().isEmpty()) {
            return "Vui lòng nhập giá bán";
        }
        long price;
        try {
            price = Long.parseLong(priceText.trim());
        } catch (NumberFormatException e) {
            return "Giá bán không hợp lệ";
        }
        if (price <= 0) {
            return "Giá bán phải lớn hơn 0";
        }
        if (price > MAX_PRODUCT_PRICE) {
            return "Giá bán vượt quá giới hạn tối đa";
        }
        return null;
    }

    public static String extractErrorMessage(String rawErrorBody, String fallbackMessage) {
        if (rawErrorBody != null && !rawErrorBody.trim().isEmpty()) {
            try {
                JSONObject json = new JSONObject(rawErrorBody);
                String msg = json.optString("message", "").trim();
                if (!msg.isEmpty()) {
                    return msg;
                }
            } catch (Exception ignored) {
                // Nếu error body không phải JSON, giữ fallbackMessage
            }
        }
        return fallbackMessage;
    }

    /**
     * Giải mã Base64URL tương thích từ Android API 24 trở lên và JVM thuần.
     */
    static String decodeBase64UrlToUtf8(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        int len = input.length();
        int mod = len % 4;
        if (mod == 1) {
            return null;
        }
        int padding = mod == 0 ? 0 : 4 - mod;
        int outLen = ((len + padding) / 4) * 3 - padding;
        byte[] output = new byte[outLen];

        int outIdx = 0;
        int buffer = 0;
        int bitsCollected = 0;
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '=') {
                break;
            }
            int val = base64UrlValue(c);
            if (val < 0) {
                return null;
            }
            buffer = (buffer << 6) | val;
            bitsCollected += 6;
            if (bitsCollected >= 8) {
                bitsCollected -= 8;
                if (outIdx < output.length) {
                    output[outIdx++] = (byte) ((buffer >> bitsCollected) & 0xFF);
                }
            }
        }
        return new String(output, 0, outIdx, StandardCharsets.UTF_8);
    }

    private static int base64UrlValue(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '-' || c == '+') return 62;
        if (c == '_' || c == '/') return 63;
        return -1;
    }
}
