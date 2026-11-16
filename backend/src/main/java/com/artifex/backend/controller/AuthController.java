package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.*;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.UserRepository;
import com.artifex.backend.service.PasswordUtils;
import com.artifex.backend.service.PhoneUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final com.artifex.backend.service.FirebaseLoginService firebaseLoginService;
    private final com.artifex.backend.service.TokenService tokenService;

    @Value("${sms.textbee.device-id:}")
    private String textBeeDeviceId;

    @Value("${sms.textbee.api-key:}")
    private String textBeeApiKey;

    // Lưu mã OTP tạm thời trong bộ nhớ với thời hạn 3 phút (180.000 ms), giới hạn tối đa 5 lần nhập sai
    private static final long OTP_EXPIRATION_MS = 180 * 1000L;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final String GMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@gmail\\.com$";
    private static final int MAX_FULL_NAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_ADDRESS_LENGTH = 255;
    private final Map<String, OtpRecord> otpStorage = new ConcurrentHashMap<>();

    private static class OtpRecord {
        final String otpCode;
        final long expiresAt;
        int failedAttempts;
        boolean verified;
        long verifiedUntil;

        OtpRecord(String otpCode, long expiresAt) {
            this.otpCode = otpCode;
            this.expiresAt = expiresAt;
            this.failedAttempts = 0;
            this.verified = false;
            this.verifiedUntil = 0L;
        }
    }

    private record ResetOtpValidation(
            User user,
            String storageKey,
            OtpRecord record,
            ResponseEntity<Map<String, Object>> errorResponse
    ) {}

    /**
     * Bước 1 Đăng ký: Kiểm tra Số điện thoại, gửi mã xác thực SMS OTP thật.
     */
    @PostMapping("/register/send-otp")
    public ResponseEntity<Map<String, Object>> sendRegisterOtp(@RequestBody RegisterOtpRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        String phone = request.getPhone() != null ? request.getPhone().trim() : "";

        if (phone.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập Số điện thoại!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String domesticPhone = PhoneUtils.toDomesticVietnamPhone(phone);
        if (!PhoneUtils.isPhoneNumber(phone)) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập đúng Số điện thoại di động (10 chữ số) để nhận mã OTP!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (!email.isEmpty()) {
            if (email.length() > MAX_EMAIL_LENGTH) {
                response.put("status", "ERROR");
                response.put("message", "Email không được vượt quá " + MAX_EMAIL_LENGTH + " ký tự!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (!email.matches(GMAIL_REGEX)) {
                response.put("status", "ERROR");
                response.put("message", "Email phải có định dạng @gmail.com hợp lệ (ví dụ: ten@gmail.com)!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (userRepository.existsByEmail(email)) {
                response.put("status", "ERROR");
                response.put("message", "Email này đã được sử dụng cho một tài khoản khác!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        }

        if (findUserByNormalizedPhone(phone).isPresent()) {
            response.put("status", "ERROR");
            response.put("message", "Số điện thoại này đã được đăng ký cho tài khoản khác!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String storageKey = "reg:" + domesticPhone;
        OtpRecord existingRecord = otpStorage.get(storageKey);
        if (existingRecord != null && System.currentTimeMillis() < existingRecord.expiresAt) {
            long remainingSeconds = Math.max(1, (existingRecord.expiresAt - System.currentTimeMillis()) / 1000L);
            response.put("status", "ERROR");
            response.put("message", "Mã OTP đã được gửi. Vui lòng đợi " + remainingSeconds + " giây trước khi yêu cầu lại!");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        // Sinh mã OTP 6 chữ số ngẫu nhiên
        String otpCode = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
        otpStorage.put(storageKey, new OtpRecord(otpCode, System.currentTimeMillis() + OTP_EXPIRATION_MS));

        // Gửi tin nhắn SMS thật qua gateway TextBee
        String smsMessage = "[ARTIFEX PC] Ma xac thuc OTP tao tai khoan Artifex cua ban la: " + otpCode + " (Hieu luc 3 phut). Tuyet doi khong chia se ma nay!";
        boolean gatewaySent = sendRealSmsViaGateway(domesticPhone, otpCode, smsMessage);

        System.out.println("==================================================================");
        System.out.println("[ARTIFEX SMS OTP] YEU CAU MA OTP DANG KY TAI KHOAN MOI");
        System.out.println("[ARTIFEX SMS OTP] Số điện thoại nhận SMS: " + domesticPhone);
        System.out.println("[ARTIFEX SMS OTP] MA XAC THUC OTP (3 phut): " + otpCode);
        System.out.println("[ARTIFEX SMS OTP] Trang thai Gateway (TextBee): " + (gatewaySent ? "DA GUI SMS THAT VE SIM DIEN THOAI" : "GUI SMS THAT BAI / CHUA CAU HINH GATEWAY"));
        System.out.println("==================================================================");

        if (!gatewaySent) {
            otpStorage.remove(storageKey);
            response.put("status", "ERROR");
            response.put("gatewaySent", false);
            response.put("message", "Không thể gửi tin nhắn SMS OTP lúc này. Vui lòng kiểm tra cấu hình hoặc kết nối SMS Gateway và thử lại!");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        String masked = maskContactInfo(domesticPhone);
        response.put("status", "SUCCESS");
        response.put("gatewaySent", true);
        response.put("message", "Đã gửi mã xác thực OTP tới số " + masked + ". Mã có hiệu lực trong 3 phút.");
        return ResponseEntity.ok(response);
    }

    /**
     * Bước 2 Đăng ký: Xác thực mã OTP và hoàn tất lưu tài khoản mới vào cơ sở dữ liệu.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        String fullName = request.getFullName() != null ? request.getFullName().trim() : "";
        String phone = request.getPhone() != null ? request.getPhone().trim() : "";
        String password = request.getPassword() != null ? request.getPassword() : "";
        String otpInput = request.getOtp() != null ? request.getOtp().trim() : "";
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String address = request.getAddress() != null ? request.getAddress().trim() : null;

        if (fullName.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập đầy đủ thông tin bắt buộc!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (fullName.length() > MAX_FULL_NAME_LENGTH) {
            response.put("status", "ERROR");
            response.put("message", "Họ và tên không được vượt quá " + MAX_FULL_NAME_LENGTH + " ký tự!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (address != null && address.length() > MAX_ADDRESS_LENGTH) {
            response.put("status", "ERROR");
            response.put("message", "Địa chỉ không được vượt quá " + MAX_ADDRESS_LENGTH + " ký tự!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String passwordError = PasswordUtils.validateRawPassword(password);
        if (passwordError != null) {
            response.put("status", "ERROR");
            response.put("message", passwordError);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String domesticPhone = PhoneUtils.toDomesticVietnamPhone(phone);
        if (!PhoneUtils.isPhoneNumber(phone) || findUserByNormalizedPhone(phone).isPresent()) {
            response.put("status", "ERROR");
            response.put("message", "Số điện thoại không hợp lệ hoặc đã được đăng ký!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Kiểm tra và bắt buộc Email phải có định dạng @gmail.com hợp lệ
        if (email.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập địa chỉ Email (@gmail.com)!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            response.put("status", "ERROR");
            response.put("message", "Email không được vượt quá " + MAX_EMAIL_LENGTH + " ký tự!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (!email.matches(GMAIL_REGEX)) {
            response.put("status", "ERROR");
            response.put("message", "Email phải có định dạng @gmail.com hợp lệ (ví dụ: ten@gmail.com)!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (userRepository.existsByEmail(email)) {
            response.put("status", "ERROR");
            response.put("message", "Email này đã được sử dụng cho một tài khoản khác!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String storageKey = "reg:" + domesticPhone;
        OtpRecord record = otpStorage.get(storageKey);

        if (record == null) {
            response.put("status", "ERROR");
            response.put("message", "Chưa có yêu cầu gửi mã OTP hoặc mã đã hết hạn. Vui lòng nhận lại mã OTP!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (System.currentTimeMillis() > record.expiresAt) {
            otpStorage.remove(storageKey);
            response.put("status", "ERROR");
            response.put("message", "Mã OTP đã hết hạn (quá 3 phút), vui lòng gửi lại mã mới!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!record.otpCode.equals(otpInput)) {
            record.failedAttempts++;
            if (record.failedAttempts >= MAX_OTP_ATTEMPTS) {
                otpStorage.remove(storageKey);
                response.put("status", "ERROR");
                response.put("message", "Bạn đã nhập sai mã OTP quá 5 lần. Mã đã bị hủy vì lý do bảo mật, vui lòng gửi lại mã mới!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            int remaining = MAX_OTP_ATTEMPTS - record.failedAttempts;
            response.put("status", "ERROR");
            response.put("message", "Mã xác thực OTP không chính xác! Bạn còn " + remaining + " lần thử.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Mọi thông tin hợp lệ -> Xóa mã OTP và lưu tài khoản
        otpStorage.remove(storageKey);

        User newUser = User.builder()
                .username("phone_" + java.util.UUID.randomUUID())
                .email(email)
                .password(PasswordUtils.hash(password))
                .fullName(fullName)
                .phone(domesticPhone)
                .address(address != null && !address.isEmpty() ? address : null)
                .avatarUrl("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80")
                .role("USER")
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            response.put("status", "ERROR");
            response.put("message", "Số điện thoại hoặc Email này đã được sử dụng cho một tài khoản khác!");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        String token = tokenService.generateToken(savedUser.getId(), savedUser.getRole());

        response.put("status", "SUCCESS");
        response.put("message", "Đăng ký tài khoản thành công!");
        response.put("token", token);
        response.put("user", savedUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        if (request.getPhone() == null || request.getPassword() == null) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập đầy đủ thông tin đăng nhập!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String input = request.getPhone().trim();
        Optional<User> userOpt = PhoneUtils.isPhoneNumber(input)
                ? findUserByNormalizedPhone(input) : Optional.empty();

        if (userOpt.isEmpty() || !PasswordUtils.matches(request.getPassword(), userOpt.get().getPassword())) {
            response.put("status", "ERROR");
            response.put("message", "Số điện thoại hoặc mật khẩu không chính xác!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        User user = userOpt.get();
        // Tự động nâng cấp mật khẩu plaintext mẫu (ví dụ từ seed_data.sql) lên BCrypt khi đăng nhập thành công
        if (!PasswordUtils.isBcryptHash(user.getPassword())) {
            user.setPassword(PasswordUtils.hash(request.getPassword()));
            User saved = userRepository.save(user);
            if (saved != null) {
                user = saved;
            }
        }

        String token = tokenService.generateToken(user.getId(), user.getRole());

        response.put("status", "SUCCESS");
        response.put("message", "Đăng nhập thành công!");
        response.put("token", token);
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/firebase")
    public ResponseEntity<Map<String, Object>> firebaseLogin(@RequestBody FirebaseLoginRequest request) {
        try {
            return ResponseEntity.ok(firebaseLoginService.login(request.getIdToken()));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                    "status", "ERROR", "message", e.getReason() != null ? e.getReason() : "Xác thực thất bại!"));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "ERROR", "message", "Tài khoản vừa thay đổi. Vui lòng đăng nhập lại."));
        }
    }

    // ==================== QUÊN MẬT KHẨU TÀI KHOẢN THƯỜNG (GỬI SMS OTP THẬT TỚI SỐ ĐIỆN THOẠI) ====================

    /**
     * Bước 1: Xác minh Số điện thoại đã đăng ký và gửi tin nhắn SMS OTP 6 chữ số.
     */
    @PostMapping("/forgot-password/send-otp")
    public ResponseEntity<Map<String, Object>> requestForgotPasswordOtp(@RequestBody ForgotPasswordOtpRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        String contactInput = request.getPhone() != null ? request.getPhone().trim() : "";
        if (!PhoneUtils.isPhoneNumber(contactInput)) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập số điện thoại hợp lệ!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        Optional<User> userOpt = findUserByNormalizedPhone(contactInput);
        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy tài khoản với số điện thoại này!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (PasswordUtils.isFirebaseOnlyAccount(userOpt.get().getPassword())) {
            response.put("status", "ERROR");
            response.put("message", "Tài khoản này đăng nhập bằng Google nên không thể đổi mật khẩu tại đây. Vui lòng tự đổi mật khẩu trên tài khoản Google bên ngoài!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        User user = userOpt.get();

        String resetKey = "reset:" + PhoneUtils.normalizeVietnamPhone(user.getPhone());
        OtpRecord existingRecord = otpStorage.get(resetKey);
        if (existingRecord != null && System.currentTimeMillis() < existingRecord.expiresAt) {
            long remainingSeconds = Math.max(1, (existingRecord.expiresAt - System.currentTimeMillis()) / 1000L);
            response.put("status", "ERROR");
            response.put("message", "Mã OTP đã được gửi. Vui lòng đợi " + remainingSeconds + " giây trước khi yêu cầu lại!");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        String otpCode = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
        otpStorage.put(resetKey, new OtpRecord(otpCode, System.currentTimeMillis() + OTP_EXPIRATION_MS));

        String targetPhone = PhoneUtils.toDomesticVietnamPhone(user.getPhone());
        boolean gatewaySent = false;
        if (!targetPhone.isEmpty()) {
            gatewaySent = sendRealSmsViaGateway(targetPhone, otpCode);
        }

        String maskedContact = maskContactInfo(!targetPhone.isEmpty() ? targetPhone : contactInput);

        System.out.println("==================================================================");
        System.out.println("[ARTIFEX SMS OTP] KHOI PHUC MAT KHAU");
        System.out.println("[ARTIFEX SMS OTP] Tai khoan: " + user.getUsername());
        System.out.println("[ARTIFEX SMS OTP] So dien thoai nhan SMS that: " + (!targetPhone.isEmpty() ? targetPhone : contactInput));
        System.out.println("[ARTIFEX SMS OTP] Ma xac thuc OTP (hieu luc 3 phut): " + otpCode);
        System.out.println("[ARTIFEX SMS OTP] Trang thai SMS Gateway (TextBee): " + (gatewaySent ? "DA GUI SMS THAT VE DIEN THOAI" : "LOI KET NOI GATEWAY"));
        System.out.println("==================================================================");

        if (!gatewaySent) {
            otpStorage.remove(resetKey);
            response.put("status", "ERROR");
            response.put("gatewaySent", false);
            response.put("message", "Không thể gửi tin nhắn SMS OTP lúc này. Vui lòng kiểm tra cấu hình hoặc kết nối SMS Gateway và thử lại!");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        response.put("status", "SUCCESS");
        response.put("message", "Đã gửi mã OTP (6 chữ số) tới " + maskedContact + ". Mã có hiệu lực trong 3 phút.");
        response.put("phone", targetPhone);
        response.put("maskedContact", maskedContact);
        response.put("targetPhone", targetPhone);
        response.put("gatewaySent", true);
        return ResponseEntity.ok(response);
    }

    /**
     * Bước 2: Xác thực mã OTP 6 chữ số trước khi chuyển sang bước đặt mật khẩu mới.
     */
    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyForgotPasswordOtp(@RequestBody ForgotPasswordVerifyOtpRequest request) {
        String phoneInput = request.getPhone() != null ? request.getPhone().trim() : "";
        String otpInput = request.getOtp() != null ? request.getOtp().trim() : "";

        ResetOtpValidation validation = validateResetOtp(phoneInput, otpInput);
        if (validation.errorResponse() != null) {
            return validation.errorResponse();
        }

        OtpRecord record = validation.record();
        record.failedAttempts = 0;
        record.verified = true;
        record.verifiedUntil = Math.max(record.expiresAt, System.currentTimeMillis() + OTP_EXPIRATION_MS);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Xác thực mã OTP thành công! Vui lòng đặt mật khẩu mới.");
        return ResponseEntity.ok(response);
    }

    /**
     * Bước 3: Kiểm tra mã OTP 6 chữ số, cập nhật mật khẩu mới và trả về thông tin User để đăng nhập ngay.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> resetPasswordWithOtp(@RequestBody ForgotPasswordRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();

        String phoneInput = request.getPhone() != null ? request.getPhone().trim() : "";
        String otpInput = request.getOtp() != null ? request.getOtp().trim() : "";
        String newPassword = request.getNewPassword() != null ? request.getNewPassword() : "";

        if (!PhoneUtils.isPhoneNumber(phoneInput) || otpInput.isEmpty() || newPassword.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập đầy đủ mã OTP và mật khẩu mới!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String passwordError = PasswordUtils.validateRawPassword(newPassword);
        if (passwordError != null) {
            response.put("status", "ERROR");
            response.put("message", passwordError);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        ResetOtpValidation validation = validateResetOtp(phoneInput, otpInput);
        if (validation.errorResponse() != null) {
            return validation.errorResponse();
        }

        User user = validation.user();
        user.setPassword(PasswordUtils.hash(newPassword));
        User savedUser = userRepository.save(user);
        otpStorage.remove(validation.storageKey());

        String token = tokenService.generateToken(savedUser.getId(), savedUser.getRole());

        response.put("status", "SUCCESS");
        response.put("message", "Đặt lại mật khẩu và đăng nhập thành công!");
        response.put("token", token);
        response.put("user", savedUser);
        return ResponseEntity.ok(response);
    }

    private ResetOtpValidation validateResetOtp(String phoneInput, String otpInput) {
        if (!PhoneUtils.isPhoneNumber(phoneInput) || otpInput.isEmpty()) {
            return new ResetOtpValidation(null, null, null,
                    errorResponse(HttpStatus.BAD_REQUEST, "Vui lòng nhập đầy đủ số điện thoại và mã OTP!"));
        }

        Optional<User> userOpt = findUserByNormalizedPhone(phoneInput);
        if (userOpt.isEmpty()) {
            return new ResetOtpValidation(null, null, null,
                    errorResponse(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản!"));
        }

        User user = userOpt.get();
        if (PasswordUtils.isFirebaseOnlyAccount(user.getPassword())) {
            return new ResetOtpValidation(null, null, null,
                    errorResponse(HttpStatus.BAD_REQUEST, "Tài khoản này đăng nhập bằng Google nên không thể đổi mật khẩu tại đây. Vui lòng tự đổi mật khẩu trên tài khoản Google bên ngoài!"));
        }

        String key = "reset:" + PhoneUtils.normalizeVietnamPhone(user.getPhone());
        OtpRecord record = otpStorage.get(key);
        if (record == null) {
            return new ResetOtpValidation(null, null, null,
                    errorResponse(HttpStatus.BAD_REQUEST, "Chưa có yêu cầu mã OTP hoặc mã đã được sử dụng!"));
        }

        long effectiveExpiry = record.verified ? record.verifiedUntil : record.expiresAt;
        if (System.currentTimeMillis() > effectiveExpiry) {
            otpStorage.remove(key);
            return new ResetOtpValidation(null, null, null,
                    errorResponse(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn (quá 3 phút), vui lòng lấy lại mã mới!"));
        }

        if (!record.otpCode.equals(otpInput)) {
            record.failedAttempts++;
            if (record.failedAttempts >= MAX_OTP_ATTEMPTS) {
                otpStorage.remove(key);
                return new ResetOtpValidation(null, null, null,
                        errorResponse(HttpStatus.BAD_REQUEST, "Bạn đã nhập sai mã OTP quá 5 lần. Mã đã bị hủy vì lý do bảo mật, vui lòng lấy lại mã mới!"));
            }
            int remaining = MAX_OTP_ATTEMPTS - record.failedAttempts;
            return new ResetOtpValidation(null, null, null,
                    errorResponse(HttpStatus.BAD_REQUEST, "Mã xác thực OTP không chính xác! Bạn còn " + remaining + " lần thử."));
        }

        return new ResetOtpValidation(user, key, record, null);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ERROR");
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        tokenService.revokeHeader(authHeader);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Đăng xuất thành công!");
        return ResponseEntity.ok(response);
    }

    // ==================== CÁC HÀM HỖ TRỢ GỬI SMS THẬT QUA TEXTBEE ====================

    private Optional<User> findUserByNormalizedPhone(String phoneInput) {
        java.util.List<String> variants = PhoneUtils.getPhoneVariants(phoneInput);
        if (variants.isEmpty()) return Optional.empty();
        Optional<User> direct = userRepository.findFirstByPhoneIn(variants);
        return direct != null ? direct : Optional.empty();
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
    protected boolean sendRealSmsViaGateway(String rawPhone, String otpCode, String customMessage) {
        String domesticPhone = PhoneUtils.toDomesticVietnamPhone(rawPhone);
        String smsContent = (customMessage != null && !customMessage.isBlank())
                ? customMessage
                : "[ARTIFEX PC] Ma xac thuc OTP cua ban la: " + otpCode + " (Hieu luc 3 phut). Tuyet doi khong chia se ma nay!";

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
                        escapeJsonString(domesticPhone),
                        escapeJsonString(smsContent)
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

    private String escapeJsonString(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    protected boolean sendRealSmsViaGateway(String rawPhone, String otpCode) {
        return sendRealSmsViaGateway(rawPhone, otpCode, null);
    }
}
