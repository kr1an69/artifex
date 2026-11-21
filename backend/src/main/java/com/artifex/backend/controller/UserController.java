package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.*;
import com.artifex.backend.entity.User;
import com.artifex.backend.entity.UserAddress;
import com.artifex.backend.repository.UserAddressRepository;
import com.artifex.backend.repository.UserRepository;
import com.artifex.backend.service.PasswordUtils;
import com.artifex.backend.service.PhoneUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final int MAX_FULL_NAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_ADDRESS_LENGTH = 255;
    private static final int MAX_DOB_LENGTH = 20;
    private static final int MAX_GENDER_LENGTH = 20;
    private static final int MAX_ADDRESS_TAG_LENGTH = 40;
    private static final String DEFAULT_ADDRESS_TAG = "🏠 Nhà riêng";
    private static final Set<String> ALLOWED_GENDERS = Set.of("Nam", "Nữ", "Khác");
    private static final DateTimeFormatter DOB_FORMATTER = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> ALLOWED_AVATAR_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp"
    );

    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final com.artifex.backend.service.TokenService tokenService;
    private final com.artifex.backend.service.CloudinaryService cloudinaryService;

    private record AuthorizedUser(User user, ResponseEntity<Map<String, Object>> errorResponse) {}

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id,
                                                            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("user", auth.user());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProfile(@PathVariable Long id,
                                                            @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                            @RequestBody UpdateProfileRequest request) {
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();

        Map<String, Object> response = new LinkedHashMap<>();
        User user = auth.user();
        boolean isGoogleAccount = PasswordUtils.isFirebaseOnlyAccount(user.getPassword())
                || (user.getFirebaseUid() != null && !user.getFirebaseUid().isBlank());

        if (request.getEmail() != null) {
            String newEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
            if (newEmail.isEmpty()) {
                response.put("status", "ERROR");
                response.put("message", "Địa chỉ Email không được để trống!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (newEmail.length() > MAX_EMAIL_LENGTH) {
                response.put("status", "ERROR");
                response.put("message", "Email không được vượt quá " + MAX_EMAIL_LENGTH + " ký tự!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            boolean unchangedExistingEmail = user.getEmail() != null && newEmail.equalsIgnoreCase(user.getEmail());
            if (isGoogleAccount && !unchangedExistingEmail) {
                response.put("status", "ERROR");
                response.put("message", "Tài khoản đăng nhập bằng Google không thể thay đổi địa chỉ Email tại đây!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (!unchangedExistingEmail && !newEmail.matches("^[a-zA-Z0-9._%+-]+@(gmail\\.com|artifex\\.vn)$")) {
                response.put("status", "ERROR");
                response.put("message", "Email phải có định dạng hợp lệ (@gmail.com hoặc @artifex.vn)!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (!unchangedExistingEmail && userRepository.existsByEmail(newEmail)) {
                response.put("status", "ERROR");
                response.put("message", "Email đã được sử dụng bởi tài khoản khác!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            user.setEmail(newEmail);
        }
        if (request.getFullName() != null) {
            String trimmedName = request.getFullName().trim();
            if (trimmedName.isEmpty()) {
                response.put("status", "ERROR");
                response.put("message", "Họ và tên không được để trống!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (trimmedName.length() > MAX_FULL_NAME_LENGTH) {
                response.put("status", "ERROR");
                response.put("message", "Họ và tên không được vượt quá " + MAX_FULL_NAME_LENGTH + " ký tự!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            user.setFullName(trimmedName);
        }
        if (request.getPhone() != null) {
            String rawPhone = request.getPhone().trim();
            if (rawPhone.isEmpty()) {
                response.put("status", "ERROR");
                response.put("message", "Số điện thoại không được để trống!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            if (!PhoneUtils.isPhoneNumber(rawPhone)) {
                response.put("status", "ERROR");
                response.put("message", "Số điện thoại không hợp lệ! Vui lòng nhập đúng 10 số (ví dụ: 09xxxxxxxx).");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            String domesticPhone = PhoneUtils.toDomesticVietnamPhone(rawPhone);
            java.util.List<String> variants = PhoneUtils.getPhoneVariants(rawPhone);
            boolean phoneTaken = userRepository.existsByPhoneInAndIdNot(variants, id);
            if (phoneTaken) {
                response.put("status", "ERROR");
                response.put("message", "Số điện thoại này đã được sử dụng bởi tài khoản khác!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            user.setPhone(domesticPhone);
        }
        if (request.getAddress() != null) {
            String trimmedAddress = request.getAddress().trim();
            if (trimmedAddress.length() > MAX_ADDRESS_LENGTH) {
                response.put("status", "ERROR");
                response.put("message", "Địa chỉ không được vượt quá " + MAX_ADDRESS_LENGTH + " ký tự!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            user.setAddress(trimmedAddress);
        }
        if (request.getDateOfBirth() != null) {
            String trimmedDob = request.getDateOfBirth().trim();
            if (!trimmedDob.isEmpty()) {
                if (trimmedDob.length() > MAX_DOB_LENGTH) {
                    response.put("status", "ERROR");
                    response.put("message", "Ngày sinh không được vượt quá " + MAX_DOB_LENGTH + " ký tự!");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
                try {
                    LocalDate parsedDob = LocalDate.parse(trimmedDob, DOB_FORMATTER);
                    if (parsedDob.isAfter(LocalDate.now())) {
                        response.put("status", "ERROR");
                        response.put("message", "Ngày sinh không được lớn hơn ngày hiện tại!");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                    }
                } catch (DateTimeParseException e) {
                    response.put("status", "ERROR");
                    response.put("message", "Ngày sinh không hợp lệ (định dạng đúng: dd/MM/yyyy)!");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                }
            }
            user.setDateOfBirth(trimmedDob);
        }
        if (request.getGender() != null) {
            String trimmedGender = request.getGender().trim();
            if (!trimmedGender.isEmpty()
                    && (trimmedGender.length() > MAX_GENDER_LENGTH || !ALLOWED_GENDERS.contains(trimmedGender))) {
                response.put("status", "ERROR");
                response.put("message", "Giới tính không hợp lệ (chỉ chấp nhận Nam, Nữ hoặc Khác)!");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            user.setGender(trimmedGender);
        }

        User updatedUser;
        try {
            updatedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            response.put("status", "ERROR");
            response.put("message", "Số điện thoại hoặc Email đã được sử dụng bởi tài khoản khác!");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }

        if (request.getAddress() != null) {
            syncDefaultAddressFromUserProfile(updatedUser != null ? updatedUser : user, request.getAddress().trim());
        }

        response.put("status", "SUCCESS");
        response.put("message", "Cập nhật thông tin thành công!");
        response.put("user", updatedUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/avatar")
    public ResponseEntity<Map<String, Object>> updateAvatar(@PathVariable Long id,
                                                           @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                           @RequestBody UpdateAvatarRequest request) {
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();

        Map<String, Object> response = new LinkedHashMap<>();
        User user = auth.user();

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
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();

        Map<String, Object> response = new LinkedHashMap<>();
        User user = auth.user();

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
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();

        Map<String, Object> response = new LinkedHashMap<>();
        User user = auth.user();

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

        long revokedAt = System.currentTimeMillis();
        user.setPassword(PasswordUtils.hash(newPassword));
        user.setTokenValidAfter(revokedAt);
        User savedUser = userRepository.save(user);
        Long targetId = savedUser != null && savedUser.getId() != null ? savedUser.getId() : user.getId();
        String role = savedUser != null ? savedUser.getRole() : user.getRole();
        tokenService.invalidateUserTokens(targetId, revokedAt);
        String newToken = tokenService.generateToken(targetId, role);

        response.put("status", "SUCCESS");
        response.put("message", "Đổi mật khẩu thành công!");
        response.put("token", newToken);
        return ResponseEntity.ok(response);
    }

    // ==================== QUẢN LÝ SỔ ĐỊA CHỈ (ĐỒNG BỘ MYSQL) ====================

    @GetMapping("/{id}/addresses")
    @Transactional
    public ResponseEntity<Map<String, Object>> getUserAddresses(@PathVariable Long id,
                                                                @RequestHeader(value = "Authorization", required = false) String authHeader) {
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();

        List<UserAddress> addresses = loadOrMigrateAddresses(auth.user());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("addresses", toAddressMapList(addresses));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/addresses")
    @Transactional
    public ResponseEntity<Map<String, Object>> addUserAddress(@PathVariable Long id,
                                                              @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                              @RequestBody UserAddressRequest request) {
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();
        if (request == null) {
            return addressError(HttpStatus.BAD_REQUEST, "Vui lòng nhập đầy đủ thông tin địa chỉ!");
        }

        String receiverName = request.getReceiverName() != null ? request.getReceiverName().trim() : "";
        String rawPhone = request.getPhone() != null ? request.getPhone().trim() : "";
        String addressLine = request.getAddressLine() != null ? request.getAddressLine().trim() : "";
        String tag = normalizeAddressTag(request.getTag());

        if (receiverName.isEmpty() || rawPhone.isEmpty() || addressLine.isEmpty()) {
            return addressError(HttpStatus.BAD_REQUEST, "Vui lòng nhập đầy đủ thông tin người nhận, số điện thoại và địa chỉ!");
        }
        if (receiverName.length() > MAX_FULL_NAME_LENGTH) {
            return addressError(HttpStatus.BAD_REQUEST, "Tên người nhận không được vượt quá " + MAX_FULL_NAME_LENGTH + " ký tự!");
        }
        if (!PhoneUtils.isPhoneNumber(rawPhone)) {
            return addressError(HttpStatus.BAD_REQUEST, "Số điện thoại người nhận không hợp lệ! Vui lòng nhập đúng 10 số (ví dụ: 09xxxxxxxx).");
        }
        if (addressLine.length() > MAX_ADDRESS_LENGTH) {
            return addressError(HttpStatus.BAD_REQUEST, "Địa chỉ không được vượt quá " + MAX_ADDRESS_LENGTH + " ký tự!");
        }
        if (tag.length() > MAX_ADDRESS_TAG_LENGTH) {
            return addressError(HttpStatus.BAD_REQUEST, "Nhãn địa chỉ không được vượt quá " + MAX_ADDRESS_TAG_LENGTH + " ký tự!");
        }

        User user = auth.user();
        List<UserAddress> existing = new ArrayList<>(loadOrMigrateAddresses(user));
        boolean shouldBeDefault = existing.isEmpty() || Boolean.TRUE.equals(request.getIsDefault());

        if (shouldBeDefault) {
            for (UserAddress item : existing) {
                if (item.isDefaultAddress()) {
                    item.setDefaultAddress(false);
                    userAddressRepository.save(item);
                }
            }
        }

        UserAddress newAddress = UserAddress.builder()
                .user(user)
                .receiverName(receiverName)
                .phone(PhoneUtils.toDomesticVietnamPhone(rawPhone))
                .addressLine(addressLine)
                .tag(tag)
                .defaultAddress(shouldBeDefault)
                .build();
        UserAddress savedAddress = userAddressRepository.save(newAddress);
        if (savedAddress == null) {
            savedAddress = newAddress;
        }
        existing.add(0, savedAddress);
        sortAddressesDefaultFirst(existing);

        User savedUser = syncUserDefaultAddressColumn(user, existing);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Thêm địa chỉ thành công!");
        response.put("address", toAddressMap(savedAddress));
        response.put("addresses", toAddressMapList(existing));
        response.put("user", savedUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/addresses/{addressId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateUserAddress(@PathVariable Long id,
                                                                 @PathVariable Long addressId,
                                                                 @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                                 @RequestBody UserAddressRequest request) {
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();
        if (request == null) {
            return addressError(HttpStatus.BAD_REQUEST, "Dữ liệu cập nhật địa chỉ không hợp lệ!");
        }

        User user = auth.user();
        Optional<UserAddress> targetOpt = userAddressRepository.findByIdAndUserId(addressId, user.getId());
        if (targetOpt.isEmpty()) {
            return addressError(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ cần cập nhật!");
        }
        UserAddress target = targetOpt.get();

        if (request.getReceiverName() != null) {
            String receiverName = request.getReceiverName().trim();
            if (receiverName.isEmpty()) {
                return addressError(HttpStatus.BAD_REQUEST, "Tên người nhận không được để trống!");
            }
            if (receiverName.length() > MAX_FULL_NAME_LENGTH) {
                return addressError(HttpStatus.BAD_REQUEST, "Tên người nhận không được vượt quá " + MAX_FULL_NAME_LENGTH + " ký tự!");
            }
            target.setReceiverName(receiverName);
        }
        if (request.getPhone() != null) {
            String rawPhone = request.getPhone().trim();
            if (rawPhone.isEmpty() || !PhoneUtils.isPhoneNumber(rawPhone)) {
                return addressError(HttpStatus.BAD_REQUEST, "Số điện thoại người nhận không hợp lệ! Vui lòng nhập đúng 10 số (ví dụ: 09xxxxxxxx).");
            }
            target.setPhone(PhoneUtils.toDomesticVietnamPhone(rawPhone));
        }
        if (request.getAddressLine() != null) {
            String addressLine = request.getAddressLine().trim();
            if (addressLine.isEmpty()) {
                return addressError(HttpStatus.BAD_REQUEST, "Địa chỉ chi tiết không được để trống!");
            }
            if (addressLine.length() > MAX_ADDRESS_LENGTH) {
                return addressError(HttpStatus.BAD_REQUEST, "Địa chỉ không được vượt quá " + MAX_ADDRESS_LENGTH + " ký tự!");
            }
            target.setAddressLine(addressLine);
        }
        if (request.getTag() != null) {
            String tag = normalizeAddressTag(request.getTag());
            if (tag.length() > MAX_ADDRESS_TAG_LENGTH) {
                return addressError(HttpStatus.BAD_REQUEST, "Nhãn địa chỉ không được vượt quá " + MAX_ADDRESS_TAG_LENGTH + " ký tự!");
            }
            target.setTag(tag);
        }

        List<UserAddress> allAddresses = new ArrayList<>(safeFindUserAddresses(user.getId()));
        if (allAddresses.stream().noneMatch(a -> addressId.equals(a.getId()))) {
            allAddresses.add(target);
        }

        if (Boolean.TRUE.equals(request.getIsDefault()) || allAddresses.size() == 1) {
            for (UserAddress item : allAddresses) {
                boolean isTarget = item == target || addressId.equals(item.getId());
                if (item.isDefaultAddress() != isTarget) {
                    item.setDefaultAddress(isTarget);
                    userAddressRepository.save(item);
                }
            }
            target.setDefaultAddress(true);
        } else if (Boolean.FALSE.equals(request.getIsDefault()) && target.isDefaultAddress()) {
            target.setDefaultAddress(false);
            userAddressRepository.save(target);
            for (UserAddress item : allAddresses) {
                if (item != target && !addressId.equals(item.getId())) {
                    item.setDefaultAddress(true);
                    userAddressRepository.save(item);
                    break;
                }
            }
        } else {
            userAddressRepository.save(target);
        }

        ensureSingleDefaultAddress(allAddresses);
        sortAddressesDefaultFirst(allAddresses);
        User savedUser = syncUserDefaultAddressColumn(user, allAddresses);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Cập nhật địa chỉ thành công!");
        response.put("address", toAddressMap(target));
        response.put("addresses", toAddressMapList(allAddresses));
        response.put("user", savedUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteUserAddress(@PathVariable Long id,
                                                                 @PathVariable Long addressId,
                                                                 @RequestHeader(value = "Authorization", required = false) String authHeader) {
        AuthorizedUser auth = authorizeAndLoadUser(id, authHeader);
        if (auth.errorResponse() != null) return auth.errorResponse();

        User user = auth.user();
        Optional<UserAddress> targetOpt = userAddressRepository.findByIdAndUserId(addressId, user.getId());
        if (targetOpt.isEmpty()) {
            return addressError(HttpStatus.NOT_FOUND, "Không tìm thấy địa chỉ cần xóa!");
        }

        UserAddress target = targetOpt.get();
        userAddressRepository.delete(target);

        List<UserAddress> remaining = new ArrayList<>(safeFindUserAddresses(user.getId()));
        remaining.removeIf(item -> item == target || addressId.equals(item.getId()));
        ensureSingleDefaultAddress(remaining);
        sortAddressesDefaultFirst(remaining);
        User savedUser = syncUserDefaultAddressColumn(user, remaining);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Đã xóa địa chỉ!");
        response.put("addresses", toAddressMapList(remaining));
        response.put("user", savedUser);
        return ResponseEntity.ok(response);
    }

    private List<UserAddress> loadOrMigrateAddresses(User user) {
        List<UserAddress> existing = new ArrayList<>(safeFindUserAddresses(user.getId()));
        if (existing.isEmpty() && user.getAddress() != null && !user.getAddress().trim().isEmpty()) {
            UserAddress migrated = UserAddress.builder()
                    .user(user)
                    .receiverName(user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName().trim() : "Khách hàng")
                    .phone(user.getPhone() != null ? user.getPhone().trim() : "")
                    .addressLine(user.getAddress().trim())
                    .tag(DEFAULT_ADDRESS_TAG)
                    .defaultAddress(true)
                    .build();
            UserAddress saved = userAddressRepository.save(migrated);
            existing.add(saved != null ? saved : migrated);
        }
        ensureSingleDefaultAddress(existing);
        sortAddressesDefaultFirst(existing);
        return existing;
    }

    private void syncDefaultAddressFromUserProfile(User user, String trimmedAddress) {
        if (user == null || user.getId() == null) return;
        List<UserAddress> existing = new ArrayList<>(safeFindUserAddresses(user.getId()));
        if (trimmedAddress.isEmpty()) {
            return;
        }
        if (existing.isEmpty()) {
            UserAddress created = UserAddress.builder()
                    .user(user)
                    .receiverName(user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName().trim() : "Khách hàng")
                    .phone(user.getPhone() != null ? user.getPhone().trim() : "")
                    .addressLine(trimmedAddress)
                    .tag(DEFAULT_ADDRESS_TAG)
                    .defaultAddress(true)
                    .build();
            userAddressRepository.save(created);
            return;
        }
        UserAddress target = existing.stream()
                .filter(UserAddress::isDefaultAddress)
                .findFirst()
                .orElse(existing.get(0));
        if (!trimmedAddress.equals(target.getAddressLine()) || !target.isDefaultAddress()) {
            target.setAddressLine(trimmedAddress);
            target.setDefaultAddress(true);
            userAddressRepository.save(target);
        }
    }

    private List<UserAddress> safeFindUserAddresses(Long userId) {
        List<UserAddress> list = userAddressRepository.findByUserIdOrderByDefaultAddressDescIdDesc(userId);
        return list != null ? list : List.of();
    }

    private void ensureSingleDefaultAddress(List<UserAddress> addresses) {
        if (addresses.isEmpty()) return;
        boolean foundDefault = false;
        for (UserAddress addr : addresses) {
            if (addr.isDefaultAddress()) {
                if (!foundDefault) {
                    foundDefault = true;
                } else {
                    addr.setDefaultAddress(false);
                    userAddressRepository.save(addr);
                }
            }
        }
        if (!foundDefault) {
            UserAddress first = addresses.get(0);
            first.setDefaultAddress(true);
            userAddressRepository.save(first);
        }
    }

    private void sortAddressesDefaultFirst(List<UserAddress> addresses) {
        addresses.sort(Comparator
                .comparing(UserAddress::isDefaultAddress, Comparator.reverseOrder())
                .thenComparing(a -> a.getId() != null ? a.getId() : 0L, Comparator.reverseOrder()));
    }

    private User syncUserDefaultAddressColumn(User user, List<UserAddress> addresses) {
        String defaultAddressLine = addresses.stream()
                .filter(UserAddress::isDefaultAddress)
                .map(UserAddress::getAddressLine)
                .findFirst()
                .orElse(addresses.isEmpty() ? null : addresses.get(0).getAddressLine());
        user.setAddress(defaultAddressLine);
        User saved = userRepository.save(user);
        return saved != null ? saved : user;
    }

    private String normalizeAddressTag(String rawTag) {
        if (rawTag == null || rawTag.trim().isEmpty()) {
            return DEFAULT_ADDRESS_TAG;
        }
        return rawTag.trim();
    }

    private List<Map<String, Object>> toAddressMapList(List<UserAddress> addresses) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserAddress addr : addresses) {
            list.add(toAddressMap(addr));
        }
        return list;
    }

    private Map<String, Object> toAddressMap(UserAddress addr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", addr.getId());
        map.put("receiverName", addr.getReceiverName());
        map.put("phone", addr.getPhone());
        map.put("addressLine", addr.getAddressLine());
        map.put("tag", addr.getTag());
        map.put("isDefault", addr.isDefaultAddress());
        return map;
    }

    private ResponseEntity<Map<String, Object>> addressError(HttpStatus status, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ERROR");
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Xác thực quyền sở hữu tài nguyên để chống lỗ hổng IDOR và từ chối token đã bị vô hiệu sau khi đổi/reset mật khẩu.
     */
    private AuthorizedUser authorizeAndLoadUser(Long targetUserId, String authHeader) {
        com.artifex.backend.service.TokenService.TokenSession session = tokenService.validateHeader(authHeader);
        if (session == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Vui lòng đăng nhập để thực hiện thao tác này!");
            return new AuthorizedUser(null, ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
        }
        if (!session.getUserId().equals(targetUserId) && !"ADMIN".equalsIgnoreCase(session.getRole())) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Bạn không có quyền thao tác trên tài khoản khác!");
            return new AuthorizedUser(null, ResponseEntity.status(HttpStatus.FORBIDDEN).body(response));
        }
        Optional<User> userOpt = userRepository.findById(targetUserId);
        if (userOpt.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Không tìm thấy người dùng!");
            return new AuthorizedUser(null, ResponseEntity.status(HttpStatus.NOT_FOUND).body(response));
        }
        User user = userOpt.get();
        if (session.getUserId().equals(user.getId())
                && tokenService.isTokenRevokedForUser(session, user)) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "ERROR");
            response.put("message", "Phiên đăng nhập đã hết hiệu lực do mật khẩu vừa thay đổi. Vui lòng đăng nhập lại!");
            return new AuthorizedUser(null, ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
        }
        return new AuthorizedUser(user, null);
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
