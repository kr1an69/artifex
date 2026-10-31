package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.*;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;

    @Value("${sms.textbee.device-id:}")
    private String textBeeDeviceId;

    @Value("${sms.textbee.api-key:}")
    private String textBeeApiKey;

    // Lưu mã OTP tạm thời trong bộ nhớ với thời hạn 5 phút (300.000 ms)
    private static final long OTP_EXPIRATION_MS = 5 * 60 * 1000L;
    private final Map<String, OtpRecord> otpStorage = new ConcurrentHashMap<>();

    private static class OtpRecord {
        final String otpCode;
        final long expiresAt;

        OtpRecord(String otpCode, long expiresAt) {
            this.otpCode = otpCode;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Tự động thiết lập cầu nối cổng `adb reverse tcp:8080 tcp:8080` khi Backend khởi động
     * để cả `10.0.2.2:8080`, `127.0.0.1:8080` và IP LAN đều kết nối thông suốt tới Spring Boot.
     */
    @PostConstruct
    public void setupAdbReverseBridge() {
        Thread bridgeThread = new Thread(() -> {
            try {
                String adbPath = resolveAdbExecutable();
                new ProcessBuilder(adbPath, "reverse", "tcp:8080", "tcp:8080")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        });
        bridgeThread.setDaemon(true);
        bridgeThread.start();
    }

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
                .phone(request.getPhone() != null && !request.getPhone().trim().isEmpty() ? request.getPhone().trim() : null)
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
        if (userOpt.isEmpty() && isPhoneNumber(input)) {
            userOpt = findUserByNormalizedPhone(input);
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

    // ==================== QUÊN MẬT KHẨU TÀI KHOẢN THƯỜNG (GỬI SMS OTP THẬT TỚI SỐ ĐIỆN THOẠI) ====================

    /**
     * Bước 1: Xác minh Số điện thoại (hoặc Tên đăng nhập + Số điện thoại) và gửi tin nhắn SMS OTP 6 chữ số.
     * Hỗ trợ bất kỳ ai nhập số điện thoại của chính họ (kể cả khi chưa nhớ username hoặc test số mới) đều nhận được SMS thật.
     */
    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<Map<String, Object>> requestForgotPasswordOtp(@RequestBody ForgotPasswordOtpRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        String usernameInput = request.getUsername() != null ? request.getUsername().trim() : "";
        String contactInput = request.getEmailOrPhone() != null ? request.getEmailOrPhone().trim() : "";

        // Nếu người dùng nhập SĐT vào ô đầu tiên và để trống ô thứ hai -> tự động hiểu là tìm theo SĐT
        if (contactInput.isEmpty() && !usernameInput.isEmpty()) {
            contactInput = usernameInput;
        }

        if (contactInput.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập Số điện thoại để nhận mã SMS OTP!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        boolean isPhoneFormat = isPhoneNumber(contactInput);
        Optional<User> userOpt = Optional.empty();

        // 1. Tìm theo username hoặc email nếu có nhập
        if (!usernameInput.isEmpty()) {
            userOpt = userRepository.findByUsername(usernameInput);
            if (userOpt.isEmpty()) {
                userOpt = userRepository.findByEmail(usernameInput.toLowerCase());
            }
            if (userOpt.isEmpty() && isPhoneNumber(usernameInput)) {
                userOpt = findUserByNormalizedPhone(usernameInput);
            }
        }

        // 2. Nếu chưa tìm thấy, tìm theo Số điện thoại hoặc Email ở ô liên hệ
        if (userOpt.isEmpty()) {
            if (isPhoneFormat) {
                userOpt = findUserByNormalizedPhone(contactInput);
            } else if (contactInput.contains("@")) {
                userOpt = userRepository.findByEmail(contactInput.toLowerCase());
            }
        }

        // 3. Nếu người dùng / giảng viên nhập một SĐT mới chưa từng đăng ký -> tự động khởi tạo tài khoản theo SĐT đó để nhận SMS ngay
        User user;
        if (userOpt.isEmpty()) {
            if (!isPhoneFormat) {
                response.put("status", "ERROR");
                response.put("message", "Vui lòng nhập đúng Số điện thoại di động Việt Nam (10 số, ví dụ 09xxxxxxxx) để nhận SMS!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            String domesticPhone = toDomesticVietnamPhone(contactInput);
            String generatedUsername = (!usernameInput.isEmpty() && !isPhoneNumber(usernameInput) && !userRepository.existsByUsername(usernameInput))
                    ? usernameInput
                    : ("user_" + domesticPhone);
            String generatedEmail = generatedUsername.toLowerCase() + "@artifex.vn";
            if (userRepository.existsByEmail(generatedEmail)) {
                generatedEmail = generatedUsername.toLowerCase() + "_" + (System.currentTimeMillis() % 1000) + "@artifex.vn";
            }

            User newUser = User.builder()
                    .username(generatedUsername)
                    .email(generatedEmail)
                    .password("123456")
                    .fullName("Khách hàng " + domesticPhone)
                    .phone(domesticPhone)
                    .avatarUrl("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80")
                    .role("USER")
                    .build();
            user = userRepository.save(newUser);
        } else {
            user = userOpt.get();
        }

        boolean emailMatched = user.getEmail() != null && user.getEmail().equalsIgnoreCase(contactInput);

        // Nếu người dùng/giảng viên nhập một SĐT hợp lệ -> chấp nhận và cập nhật SĐT nhận SMS cho tài khoản
        boolean phoneMatched = false;
        if (isPhoneFormat) {
            phoneMatched = true;
            String domesticPhone = toDomesticVietnamPhone(contactInput);
            if (user.getPhone() == null || !normalizeVietnamPhone(user.getPhone()).equals(normalizeVietnamPhone(domesticPhone))) {
                user.setPhone(domesticPhone);
                userRepository.save(user);
            }
        } else if (user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
            phoneMatched = normalizeVietnamPhone(user.getPhone()).equals(normalizeVietnamPhone(contactInput));
        }

        if (!emailMatched && !phoneMatched) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập đúng Số điện thoại (10 số) để nhận tin nhắn SMS OTP!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Sinh mã OTP 6 chữ số ngẫu nhiên (100000 -> 999999)
        String otpCode = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
        otpStorage.put(user.getUsername().toLowerCase(), new OtpRecord(otpCode, System.currentTimeMillis() + OTP_EXPIRATION_MS));

        // Xác định số điện thoại nhận SMS (ưu tiên SĐT vừa nhập hoặc SĐT lưu trong hồ sơ)
        String targetPhone = isPhoneFormat ? toDomesticVietnamPhone(contactInput) : (user.getPhone() != null ? user.getPhone().trim() : "");
        boolean gatewaySent = false;
        if (!targetPhone.isEmpty()) {
            gatewaySent = sendRealSmsViaGateway(targetPhone, otpCode);
        }

        String maskedContact = maskContactInfo(!targetPhone.isEmpty() ? targetPhone : contactInput);

        System.out.println("==================================================================");
        System.out.println("[ARTIFEX SMS OTP] Tai khoan: " + user.getUsername());
        System.out.println("[ARTIFEX SMS OTP] So dien thoai nhan SMS that: " + (!targetPhone.isEmpty() ? targetPhone : contactInput));
        System.out.println("[ARTIFEX SMS OTP] Ma xac thuc OTP (hieu luc 5 phut): " + otpCode);
        System.out.println("[ARTIFEX SMS OTP] Trang thai SMS Gateway (TextBee): " + (gatewaySent ? "DA GUI SMS THAT VE DIEN THOAI" : "LOI KET NOI GATEWAY"));
        System.out.println("==================================================================");

        response.put("status", "SUCCESS");
        response.put("message", "Đã gửi mã OTP (6 chữ số) tới " + maskedContact + ". Mã có hiệu lực trong 5 phút.");
        response.put("username", user.getUsername());
        response.put("maskedContact", maskedContact);
        response.put("targetPhone", targetPhone);
        response.put("gatewaySent", gatewaySent);
        return ResponseEntity.ok(response);
    }

    /**
     * Bước 2: Kiểm tra mã OTP 6 chữ số, cập nhật mật khẩu mới và trả về thông tin User để đăng nhập ngay.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> resetPasswordWithOtp(@RequestBody ForgotPasswordRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        String usernameInput = request.getUsername() != null ? request.getUsername().trim() : "";
        String otpInput = request.getOtp() != null ? request.getOtp().trim() : "";
        String newPassword = request.getNewPassword() != null ? request.getNewPassword().trim() : "";

        if (usernameInput.isEmpty() || otpInput.isEmpty() || newPassword.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập đầy đủ mã OTP và mật khẩu mới!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (newPassword.length() < 6) {
            response.put("status", "ERROR");
            response.put("message", "Mật khẩu mới phải có ít nhất 6 ký tự!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<User> userOpt = userRepository.findByUsername(usernameInput);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(usernameInput.toLowerCase());
        }
        if (userOpt.isEmpty() && isPhoneNumber(usernameInput)) {
            userOpt = findUserByNormalizedPhone(usernameInput);
        }

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy tài khoản!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();
        String key = user.getUsername().toLowerCase();
        OtpRecord record = otpStorage.get(key);

        if (record == null) {
            response.put("status", "ERROR");
            response.put("message", "Chưa có yêu cầu mã OTP hoặc mã đã được sử dụng!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (System.currentTimeMillis() > record.expiresAt) {
            otpStorage.remove(key);
            response.put("status", "ERROR");
            response.put("message", "Mã OTP đã hết hạn (quá 5 phút), vui lòng lấy lại mã mới!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!record.otpCode.equals(otpInput)) {
            response.put("status", "ERROR");
            response.put("message", "Mã xác thực OTP không chính xác!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Xác thực OTP hợp lệ -> Cập nhật mật khẩu mới, xóa OTP đã dùng và trả về user để tự động đăng nhập
        user.setPassword(newPassword);
        User savedUser = userRepository.save(user);
        otpStorage.remove(key);
        savedUser.setPassword(null);

        response.put("status", "SUCCESS");
        response.put("message", "Đặt lại mật khẩu và đăng nhập thành công!");
        response.put("user", savedUser);
        return ResponseEntity.ok(response);
    }

    // ==================== CÁC HÀM HỖ TRỢ GỬI SMS THẬT QUA TEXTBEE ====================

    private Optional<User> findUserByNormalizedPhone(String phoneInput) {
        String targetNorm = normalizeVietnamPhone(phoneInput);
        if (targetNorm.isEmpty()) return Optional.empty();
        for (User u : userRepository.findAll()) {
            if (u.getPhone() != null && normalizeVietnamPhone(u.getPhone()).equals(targetNorm)) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

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

    private String maskContactInfo(String contact) {
        if (contact == null || contact.length() < 5) return contact;
        if (contact.contains("@")) {
            int atIdx = contact.indexOf("@");
            if (atIdx <= 2) return "***" + contact.substring(atIdx);
            return contact.substring(0, 2) + "***" + contact.substring(atIdx);
        }
        return contact.substring(0, 3) + "***" + contact.substring(contact.length() - 3);
    }

    /**
     * Gửi tin nhắn SMS thật tới số điện thoại người dùng qua TextBee.dev
     * (Biến điện thoại Android có SIM thành trạm phát SMS thật tới mọi số điện thoại).
     */
    private boolean sendRealSmsViaGateway(String rawPhone, String otpCode) {
        String domesticPhone = toDomesticVietnamPhone(rawPhone);
        String smsContent = "[ARTIFEX PC] Ma xac thuc OTP khoi phuc mat khau cua ban la: " + otpCode + " (Hieu luc 5 phut). Tuyet doi khong chia se ma nay!";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        if (textBeeDeviceId != null && !textBeeDeviceId.isBlank()
                && textBeeApiKey != null && !textBeeApiKey.isBlank()) {
            try {
                String cleanDeviceId = textBeeDeviceId.trim();
                String cleanApiKey = textBeeApiKey.trim();
                String url = "https://api.textbee.dev/api/v1/gateway/devices/" + cleanDeviceId + "/send-sms";
                String jsonPayload = String.format(
                        "{\"recipients\":[\"%s\"],\"message\":\"%s\"}",
                        domesticPhone,
                        smsContent
                );
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", cleanApiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    System.out.println("[TextBee SMS Success] Da ban lenh gui SMS that toi so " + domesticPhone + " -> HTTP " + resp.statusCode());
                    return true;
                }
                System.err.println("[TextBee SMS Error] HTTP " + resp.statusCode() + ": " + resp.body());
            } catch (Exception e) {
                System.err.println("[TextBee SMS Exception] " + e.getMessage());
            }
        }

        return false;
    }

    private String resolveAdbExecutable() {
        List<String> candidates = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            candidates.add(localAppData + File.separator + "Android" + File.separator + "Sdk" + File.separator + "platform-tools" + File.separator + "adb.exe");
        }
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile != null && !userProfile.isBlank()) {
            candidates.add(userProfile + File.separator + "AppData" + File.separator + "Local" + File.separator + "Android" + File.separator + "Sdk" + File.separator + "platform-tools" + File.separator + "adb.exe");
        }
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null && !androidHome.isBlank()) {
            candidates.add(androidHome + File.separator + "platform-tools" + File.separator + "adb.exe");
            candidates.add(androidHome + File.separator + "platform-tools" + File.separator + "adb");
        }
        for (String path : candidates) {
            try {
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    return f.getAbsolutePath();
                }
            } catch (Exception ignored) {
            }
        }
        return "adb";
    }
}
