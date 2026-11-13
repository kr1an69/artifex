package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.*;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.UserRepository;
import com.artifex.backend.service.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private static final long MAX_AVATAR_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_AVATAR_URL_LENGTH = 500;
    private static final Set<String> ALLOWED_AVATAR_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp"
    );

    private final UserRepository userRepository;
    private final com.artifex.backend.service.TokenService tokenService;
    private final com.artifex.backend.service.CloudinaryService cloudinaryService;

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id,
                                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        ResponseEntity<Map<String, Object>> authError = verifyUserAuthorization(id, authHeader);
        if (authError != null) return authError;

        Map<String, Object> response = new LinkedHashMap<>();
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        response.put("status", "SUCCESS");
        response.put("user", user);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProfile(@PathVariable Long id,
                                                            @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                            @RequestBody UpdateProfileRequest request) {
        ResponseEntity<Map<String, Object>> authError = verifyUserAuthorization(id, authHeader);
        if (authError != null) return authError;

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
            boolean unchangedExistingEmail = user.getEmail() != null && newEmail.equalsIgnoreCase(user.getEmail());
            if (!unchangedExistingEmail && !newEmail.matches("^[a-zA-Z0-9._%+-]+@(gmail\\.com|artifex\\.vn)$")) {
                response.put("status", "ERROR");
                response.put("message", "Email phải có định dạng hợp lệ (@gmail.com hoặc @artifex.vn)!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
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
            java.util.List<String> variants = getPhoneVariants(rawPhone);
            boolean phoneTaken = userRepository.existsByPhoneInAndIdNot(variants, id);
            if (!phoneTaken) {
                String targetNorm = normalizeVietnamPhone(rawPhone);
                java.util.List<User> all = userRepository.findAll();
                if (all != null && !all.isEmpty()) {
                    phoneTaken = all.stream()
                            .anyMatch(u -> !u.getId().equals(id) && u.getPhone() != null && normalizeVietnamPhone(u.getPhone()).equals(targetNorm));
                }
            }
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

        response.put("status", "SUCCESS");
        response.put("message", "Cập nhật thông tin thành công!");
        response.put("user", updatedUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/avatar")
    public ResponseEntity<Map<String, Object>> updateAvatar(@PathVariable Long id,
                                                           @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                           @RequestBody UpdateAvatarRequest request) {
        ResponseEntity<Map<String, Object>> authError = verifyUserAuthorization(id, authHeader);
        if (authError != null) return authError;

        Map<String, Object> response = new LinkedHashMap<>();
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        String avatarUrl = (request != null && request.getAvatarUrl() != null)
                ? request.getAvatarUrl().trim()
                : "";
        if (avatarUrl.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Đường dẫn avatar không hợp lệ!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (avatarUrl.length() > MAX_AVATAR_URL_LENGTH) {
            response.put("status", "ERROR");
            response.put("message", "Đường dẫn avatar không được vượt quá " + MAX_AVATAR_URL_LENGTH + " ký tự!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (!isValidHttpUrl(avatarUrl)) {
            response.put("status", "ERROR");
            response.put("message", "Đường dẫn avatar phải là URL http hoặc https hợp lệ!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        User user = userOpt.get();
        user.setAvatarUrl(avatarUrl);

        User updatedUser = userRepository.save(user);

        response.put("status", "SUCCESS");
        response.put("message", "Cập nhật ảnh đại diện thành công!");
        response.put("user", updatedUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Tải file ảnh trực tiếp lên Cloudinary thông qua Cloudinary Java SDK của Spring Boot backend.
     * Lưu URL trả về vào cơ sở dữ liệu MySQL và trả về thông tin User mới nhất.
     * POST /api/users/{id}/avatar/upload
     */
    @PostMapping(value = "/{id}/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAvatarFile(@PathVariable Long id,
                                                                @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                                @RequestParam("file") MultipartFile file) {
        ResponseEntity<Map<String, Object>> authError = verifyUserAuthorization(id, authHeader);
        if (authError != null) return authError;

        Map<String, Object> response = new LinkedHashMap<>();
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        if (file == null || file.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng chọn tệp ảnh để tải lên!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String fileValidationError = validateAvatarFile(file);
        if (fileValidationError != null) {
            response.put("status", "ERROR");
            response.put("message", fileValidationError);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            String uploadedUrl = cloudinaryService.uploadImage(file, "artifex/avatars");
            User user = userOpt.get();
            user.setAvatarUrl(uploadedUrl);
            User updatedUser = userRepository.save(user);

            response.put("status", "SUCCESS");
            response.put("message", "Tải lên và cập nhật ảnh đại diện thành công!");
            response.put("avatarUrl", uploadedUrl);
            response.put("user", updatedUser);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("status", "ERROR");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "Lỗi upload ảnh lên Cloudinary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, Object>> changePassword(@PathVariable Long id,
                                                              @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                              @RequestBody ChangePasswordRequest request) {
        ResponseEntity<Map<String, Object>> authError = verifyUserAuthorization(id, authHeader);
        if (authError != null) return authError;

        Map<String, Object> response = new LinkedHashMap<>();
        Optional<User> userOpt = userRepository.findById(id);

        if (userOpt.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        User user = userOpt.get();

        // Kiểm tra chặn nếu là tài khoản đăng nhập qua Google
        if (PasswordUtils.isFirebaseOnlyAccount(user.getPassword())) {
            response.put("status", "ERROR");
            response.put("message", "Tài khoản của bạn đăng nhập bằng Google nên không thể đổi mật khẩu tại đây. Vui lòng tự đổi mật khẩu trên tài khoản Google bên ngoài!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Validate dữ liệu đầu vào (KHÔNG trim mật khẩu để đồng nhất toàn hệ thống)
        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();
        if (oldPassword == null || oldPassword.isEmpty()) {
            response.put("status", "ERROR");
            response.put("message", "Vui lòng nhập mật khẩu hiện tại!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String passwordError = PasswordUtils.validateRawPassword(newPassword);
        if (passwordError != null) {
            response.put("status", "ERROR");
            response.put("message", passwordError);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Kiểm tra khớp mật khẩu cũ
        if (!PasswordUtils.matches(oldPassword, user.getPassword())) {
            response.put("status", "ERROR");
            response.put("message", "Mật khẩu hiện tại không chính xác!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        user.setPassword(PasswordUtils.hash(newPassword));
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

    private java.util.List<String> getPhoneVariants(String phoneInput) {
        if (phoneInput == null || phoneInput.isBlank()) return java.util.Collections.emptyList();
        String domestic = toDomesticVietnamPhone(phoneInput);
        String e164 = normalizeVietnamPhone(phoneInput);
        String noPlus = e164.startsWith("+") ? e164.substring(1) : e164;
        return java.util.stream.Stream.of(domestic, e164, noPlus)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Xác thực quyền sở hữu tài nguyên để chống lỗ hổng IDOR.
     * Chỉ cho phép chính chủ nhân của tài khoản hoặc tài khoản có role ADMIN thao tác.
     */
    private ResponseEntity<Map<String, Object>> verifyUserAuthorization(Long targetUserId, String authHeader) {
        com.artifex.backend.service.TokenService.TokenSession session = tokenService.validateHeader(authHeader);
        if (session == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Vui lòng đăng nhập để thực hiện thao tác này!");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!session.getUserId().equals(targetUserId) && !"ADMIN".equalsIgnoreCase(session.getRole())) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Bạn không có quyền thao tác trên tài khoản khác!");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        return null;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ERROR");
        response.put("message", "Ảnh đại diện vượt quá dung lượng tối đa 5MB!");
        return ResponseEntity.status(413).body(response);
    }

    private String validateAvatarFile(MultipartFile file) {
        if (file.getSize() > MAX_AVATAR_FILE_BYTES) {
            return "Ảnh đại diện không được vượt quá 5MB!";
        }
        String rawMime = file.getContentType();
        String mime = rawMime != null ? rawMime.trim().toLowerCase(Locale.ROOT) : "";
        int semiIdx = mime.indexOf(';');
        if (semiIdx >= 0) {
            mime = mime.substring(0, semiIdx).trim();
        }
        if (!ALLOWED_AVATAR_MIME_TYPES.contains(mime)) {
            return "Chỉ hỗ trợ định dạng ảnh JPG, PNG hoặc WEBP!";
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.trim().isEmpty()) {
            int dotIdx = originalName.lastIndexOf('.');
            if (dotIdx >= 0 && dotIdx < originalName.length() - 1) {
                String ext = originalName.substring(dotIdx + 1).trim().toLowerCase(Locale.ROOT);
                if (!ALLOWED_AVATAR_EXTENSIONS.contains(ext)) {
                    return "Đuôi tệp không hợp lệ! Chỉ hỗ trợ .jpg, .jpeg, .png hoặc .webp.";
                }
            }
        }

        if (!hasValidImageMagicBytes(file)) {
            return "Tệp tải lên không phải ảnh hợp lệ (JPG, PNG hoặc WEBP)!";
        }
        return null;
    }

    private boolean hasValidImageMagicBytes(MultipartFile file) {
        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream in = file.getInputStream()) {
            if (in == null) return false;
            bytesRead = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            return false;
        }
        // JPEG: FF D8 FF
        if (bytesRead >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytesRead >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return true;
        }
        // WEBP: RIFF....WEBP
        return bytesRead >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    private boolean isValidHttpUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme != null
                    && (scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    && host != null && !host.trim().isEmpty();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
