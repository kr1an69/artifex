package com.artifex.backend.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tiện ích chuẩn hóa và kiểm tra định dạng số điện thoại di động Việt Nam.
 */
public final class PhoneUtils {

    private PhoneUtils() {
    }

    public static boolean isPhoneNumber(String input) {
        if (input == null) return false;
        String cleaned = input.replaceAll("[\\s\\-\\.\\(\\)]", "");
        return cleaned.matches("^(\\+?840?|0)[35789]\\d{8}$");
    }

    public static String normalizeVietnamPhone(String rawPhone) {
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

    public static String toDomesticVietnamPhone(String rawPhone) {
        if (rawPhone == null) return "";
        String e164 = normalizeVietnamPhone(rawPhone);
        if (e164.startsWith("+84")) {
            return "0" + e164.substring(3);
        }
        return rawPhone.replaceAll("[^0-9]", "");
    }

    public static List<String> getPhoneVariants(String phoneInput) {
        if (phoneInput == null || phoneInput.isBlank()) {
            return Collections.emptyList();
        }
        String domestic = toDomesticVietnamPhone(phoneInput);
        String e164 = normalizeVietnamPhone(phoneInput);
        String noPlus = e164.startsWith("+") ? e164.substring(1) : e164;
        return Stream.of(domestic, e164, noPlus)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .toList();
    }
}
