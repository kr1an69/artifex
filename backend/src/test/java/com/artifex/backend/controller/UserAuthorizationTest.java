package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.ChangePasswordRequest;
import com.artifex.backend.dto.AuthDTO.UpdateAvatarRequest;
import com.artifex.backend.dto.AuthDTO.UpdateProfileRequest;
import com.artifex.backend.dto.AuthDTO.UserAddressRequest;
import com.artifex.backend.entity.User;
import com.artifex.backend.entity.UserAddress;
import com.artifex.backend.repository.UserAddressRepository;
import com.artifex.backend.repository.UserRepository;
import com.artifex.backend.service.CloudinaryService;
import com.artifex.backend.service.PasswordUtils;
import com.artifex.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserAuthorizationTest {

    private UserRepository userRepository;
    private UserAddressRepository userAddressRepository;
    private TokenService tokenService;
    private CloudinaryService cloudinaryService;
    private UserController userController;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        userAddressRepository = mock(UserAddressRepository.class);
        tokenService = new TokenService();
        cloudinaryService = mock(CloudinaryService.class);
        userController = new UserController(userRepository, userAddressRepository, tokenService, cloudinaryService);
    }

    @Test
    void requestWithoutTokenReturnsUnauthorized() {
        ResponseEntity<Map<String, Object>> response = userController.getUserById(1L, null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("ERROR", response.getBody().get("status"));
    }

    @Test
    void userCannotModifyAnotherUserProfile_IDOR_Blocked() {
        // User 1 đăng nhập và có token
        String user1Token = tokenService.generateToken(1L, "USER");

        // User 1 cố tình gọi API sửa profile của User 2
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Attacker Name");

        ResponseEntity<Map<String, Object>> response = userController.updateProfile(2L, "Bearer " + user1Token, request);

        // Hệ thống chặn với lỗi 403 Forbidden!
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ERROR", response.getBody().get("status"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void userCanModifyOwnProfile() {
        // User 1 đăng nhập và có token
        String user1Token = tokenService.generateToken(1L, "USER");

        User existingUser = User.builder()
                .id(1L)
                .username("user1")
                .fullName("Original Name")
                .email("user1@gmail.com")
                .password("123456")
                .role("USER")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");

        ResponseEntity<Map<String, Object>> response = userController.updateProfile(1L, "Bearer " + user1Token, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("status"));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void changePasswordHashesWithBcryptAndRejectsAnyWhitespace() {
        String user1Token = tokenService.generateToken(1L, "USER");
        User existingUser = User.builder()
                .id(1L)
                .username("user1")
                .fullName("Original Name")
                .email("user1@gmail.com")
                .password(PasswordUtils.hash("oldPass123"))
                .role("USER")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Mật khẩu mới chứa khoảng trắng ở bất kỳ vị trí nào -> bị từ chối
        for (String badNewPass : java.util.List.of(" newpass1", "new pass1", "newpass1 ")) {
            ResponseEntity<Map<String, Object>> badResp = userController.changePassword(
                    1L, "Bearer " + user1Token, new ChangePasswordRequest("oldPass123", badNewPass));
            assertEquals(HttpStatus.BAD_REQUEST, badResp.getStatusCode());
        }

        // Nhập đúng mật khẩu cũ, đặt mật khẩu mới là "FIREBASE_AUTH" -> băm BCrypt an toàn, không khóa tài khoản
        ResponseEntity<Map<String, Object>> okResp = userController.changePassword(
                1L, "Bearer " + user1Token, new ChangePasswordRequest("oldPass123", "FIREBASE_AUTH"));
        assertEquals(HttpStatus.OK, okResp.getStatusCode());
        org.junit.jupiter.api.Assertions.assertTrue(PasswordUtils.isBcryptHash(existingUser.getPassword()));
        org.junit.jupiter.api.Assertions.assertTrue(PasswordUtils.matches("FIREBASE_AUTH", existingUser.getPassword()));
        org.junit.jupiter.api.Assertions.assertNotNull(existingUser.getTokenValidAfter());

        // Token cũ bị vô hiệu ngay lập tức; token mới trả về hoạt động bình thường
        assertEquals(HttpStatus.UNAUTHORIZED,
                userController.getUserById(1L, "Bearer " + user1Token).getStatusCode());
        String freshToken = (String) okResp.getBody().get("token");
        org.junit.jupiter.api.Assertions.assertNotNull(freshToken);
        assertEquals(HttpStatus.OK,
                userController.getUserById(1L, "Bearer " + freshToken).getStatusCode());

        // Ngay cả sau khi khởi tạo TokenService mới (restart server), token cũ vẫn bị chặn nhờ tokenValidAfter trong DB
        UserController restartedController = new UserController(userRepository, userAddressRepository, new TokenService(), cloudinaryService);
        assertEquals(HttpStatus.UNAUTHORIZED,
                restartedController.getUserById(1L, "Bearer " + user1Token).getStatusCode());
        assertEquals(HttpStatus.OK,
                restartedController.getUserById(1L, "Bearer " + freshToken).getStatusCode());
    }

    @Test
    void prodProfileRejectsMissingOrDefaultJwtSecret() {
        org.springframework.mock.env.MockEnvironment prodEnv = new org.springframework.mock.env.MockEnvironment()
                .withProperty("spring.profiles.active", "prod");
        prodEnv.setActiveProfiles("prod");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new TokenService("", prodEnv));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new TokenService("ArtifexSecretKeyForTokenSigning2026Secure256BitKey!", prodEnv));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> new TokenService("MyCustomStrongProductionSecretKey2026!", prodEnv));
    }

    @Test
    void seedUserWithArtifexEmailCanUpdateProfile() {
        String user1Token = tokenService.generateToken(1L, "USER");
        User seedUser = User.builder()
                .id(1L)
                .username("nguyenvana")
                .fullName("Nguyễn Văn A")
                .email("vana@artifex.vn")
                .phone("0901234567")
                .password("123456")
                .role("USER")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(seedUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Nguyễn Văn A Mới");
        request.setEmail("vana@artifex.vn");
        request.setPhone("0901234567");

        ResponseEntity<Map<String, Object>> response = userController.updateProfile(1L, "Bearer " + user1Token, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("status"));
    }

    @Test
    void uploadAvatarFileSucceeds() throws Exception {
        String user1Token = tokenService.generateToken(1L, "USER");
        User existingUser = User.builder()
                .id(1L)
                .username("user1")
                .fullName("User One")
                .email("user1@gmail.com")
                .password("123456")
                .role("USER")
                .avatarUrl("old_url")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "avatar.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}
        );
        when(cloudinaryService.uploadImage(mockFile, "artifex/avatars")).thenReturn("https://res.cloudinary.com/demo/image/upload/new_avatar.jpg");

        ResponseEntity<Map<String, Object>> response = userController.uploadAvatarFile(1L, "Bearer " + user1Token, mockFile);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("https://res.cloudinary.com/demo/image/upload/new_avatar.jpg", response.getBody().get("avatarUrl"));
        assertEquals("https://res.cloudinary.com/demo/image/upload/new_avatar.jpg", existingUser.getAvatarUrl());
        verify(userRepository).save(existingUser);
    }

    @Test
    void uploadAvatarFileRejectsFakeOrUnsupportedFilesAndOversize() throws Exception {
        String user1Token = tokenService.generateToken(1L, "USER");
        User existingUser = User.builder().id(1L).username("user1").role("USER").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        MockMultipartFile fakeJpg = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "not-real-image-data".getBytes()
        );
        ResponseEntity<Map<String, Object>> fakeResp = userController.uploadAvatarFile(1L, "Bearer " + user1Token, fakeJpg);
        assertEquals(HttpStatus.BAD_REQUEST, fakeResp.getStatusCode());

        MockMultipartFile badMime = new MockMultipartFile(
                "file", "note.txt", "text/plain", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
        );
        ResponseEntity<Map<String, Object>> mimeResp = userController.uploadAvatarFile(1L, "Bearer " + user1Token, badMime);
        assertEquals(HttpStatus.BAD_REQUEST, mimeResp.getStatusCode());

        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(6L * 1024 * 1024);
        ResponseEntity<Map<String, Object>> sizeResp = userController.uploadAvatarFile(1L, "Bearer " + user1Token, oversized);
        assertEquals(HttpStatus.BAD_REQUEST, sizeResp.getStatusCode());

        verify(cloudinaryService, never()).uploadImage(any(), any());
    }

    @Test
    void updateAvatarValidatesHttpUrlAndMaxLength() {
        String user1Token = tokenService.generateToken(1L, "USER");
        User existingUser = User.builder().id(1L).username("user1").role("USER").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));

        UpdateAvatarRequest badScheme = new UpdateAvatarRequest("javascript:alert(1)");
        ResponseEntity<Map<String, Object>> schemeResp = userController.updateAvatar(1L, "Bearer " + user1Token, badScheme);
        assertEquals(HttpStatus.BAD_REQUEST, schemeResp.getStatusCode());

        String tooLongUrl = "https://res.cloudinary.com/demo/image/upload/" + "a".repeat(500) + ".jpg";
        UpdateAvatarRequest longReq = new UpdateAvatarRequest(tooLongUrl);
        ResponseEntity<Map<String, Object>> longResp = userController.updateAvatar(1L, "Bearer " + user1Token, longReq);
        assertEquals(HttpStatus.BAD_REQUEST, longResp.getStatusCode());

        verify(userRepository, never()).save(any());
    }

    @Test
    void cloudinaryServicePrefersSignedUploadAndFallsBackToUnsignedWhenUnconfigured() throws Exception {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", jpegBytes);

        // 1. Khi đã cấu hình API Key/Secret -> dùng signed upload, không gọi unsignedUpload
        com.cloudinary.Cloudinary signedCloudinary = org.mockito.Mockito.spy(new com.cloudinary.Cloudinary(
                com.cloudinary.utils.ObjectUtils.asMap("cloud_name", "wpheip7x", "api_key", "test_key", "api_secret", "test_secret")
        ));
        com.cloudinary.Uploader signedUploader = org.mockito.Mockito.mock(com.cloudinary.Uploader.class);
        org.mockito.Mockito.doReturn(signedUploader).when(signedCloudinary).uploader();
        when(signedUploader.upload(any(), any())).thenReturn(Map.of("secure_url", "https://res.cloudinary.com/wpheip7x/image/upload/signed.jpg"));

        CloudinaryService signedService = new CloudinaryService(signedCloudinary);
        assertEquals("https://res.cloudinary.com/wpheip7x/image/upload/signed.jpg",
                signedService.uploadImage(file, "artifex/avatars"));
        verify(signedUploader).upload(any(), any());
        verify(signedUploader, never()).unsignedUpload(any(), any(), any());

        // 2. Khi thiếu API Key/Secret (máy local thành viên khác) -> fallback sang unsignedUpload("artifex_avatar")
        com.cloudinary.Cloudinary missingCredsCloudinary = org.mockito.Mockito.spy(new com.cloudinary.Cloudinary(
                com.cloudinary.utils.ObjectUtils.asMap("cloud_name", "wpheip7x", "api_key", "", "api_secret", "")
        ));
        com.cloudinary.Uploader unsignedUploader = org.mockito.Mockito.mock(com.cloudinary.Uploader.class);
        org.mockito.Mockito.doReturn(unsignedUploader).when(missingCredsCloudinary).uploader();
        when(unsignedUploader.unsignedUpload(any(), eq("artifex_avatar"), any()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/wpheip7x/image/upload/unsigned.jpg"));

        CloudinaryService unconfiguredService = new CloudinaryService(missingCredsCloudinary);
        assertEquals("https://res.cloudinary.com/wpheip7x/image/upload/unsigned.jpg",
                unconfiguredService.uploadImage(file, "artifex/avatars"));
        verify(unsignedUploader, never()).upload(any(), any());
        verify(unsignedUploader).unsignedUpload(any(), eq("artifex_avatar"), any());

        // 3. Khi signed upload lỗi -> tự fallback sang unsignedUpload("artifex_avatar")
        com.cloudinary.Cloudinary fallbackCloudinary = org.mockito.Mockito.spy(new com.cloudinary.Cloudinary(
                com.cloudinary.utils.ObjectUtils.asMap("cloud_name", "wpheip7x", "api_key", "test_key", "api_secret", "test_secret")
        ));
        com.cloudinary.Uploader fallbackUploader = org.mockito.Mockito.mock(com.cloudinary.Uploader.class);
        org.mockito.Mockito.doReturn(fallbackUploader).when(fallbackCloudinary).uploader();
        when(fallbackUploader.upload(any(), any())).thenThrow(new java.io.IOException("signed upload failed"));
        when(fallbackUploader.unsignedUpload(any(), eq("artifex_avatar"), any()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/wpheip7x/image/upload/fallback.jpg"));

        CloudinaryService fallbackService = new CloudinaryService(fallbackCloudinary);
        assertEquals("https://res.cloudinary.com/wpheip7x/image/upload/fallback.jpg",
                fallbackService.uploadImage(file, "artifex/avatars"));
        verify(fallbackUploader).upload(any(), any());
        verify(fallbackUploader).unsignedUpload(any(), eq("artifex_avatar"), any());
    }

    @Test
    void tokenServiceValidateHeaderChecksTokenValidAfterDirectly() {
        String oldToken = tokenService.generateToken(1L, "USER");
        long cutoff = System.currentTimeMillis() + 5000L;
        User updatedUser = User.builder()
                .id(1L)
                .username("user1")
                .role("USER")
                .tokenValidAfter(cutoff)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(updatedUser));

        TokenService restartedTokenService = new TokenService();
        restartedTokenService.setUserRepository(userRepository);

        org.junit.jupiter.api.Assertions.assertNull(restartedTokenService.validateHeader("Bearer " + oldToken));

        tokenService.invalidateUserTokens(1L, cutoff);
        String freshToken = tokenService.generateToken(1L, "USER");
        org.junit.jupiter.api.Assertions.assertNotNull(restartedTokenService.validateHeader("Bearer " + freshToken));
    }

    @Test
    void updateProfileValidatesFieldsLengthsDobGenderAndBlocksGoogleEmailChange() {
        String userToken = tokenService.generateToken(1L, "USER");
        User regularUser = User.builder()
                .id(1L).username("user1").fullName("User One")
                .email("user1@gmail.com").phone("0901234567")
                .password(PasswordUtils.hash("123456")).role("USER").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Blank fullName -> 400
        UpdateProfileRequest blankName = new UpdateProfileRequest();
        blankName.setFullName("   ");
        assertEquals(HttpStatus.BAD_REQUEST, userController.updateProfile(1L, "Bearer " + userToken, blankName).getStatusCode());

        // Oversized fullName / address -> 400
        UpdateProfileRequest longName = new UpdateProfileRequest();
        longName.setFullName("A".repeat(101));
        assertEquals(HttpStatus.BAD_REQUEST, userController.updateProfile(1L, "Bearer " + userToken, longName).getStatusCode());

        UpdateProfileRequest longAddr = new UpdateProfileRequest();
        longAddr.setAddress("B".repeat(256));
        assertEquals(HttpStatus.BAD_REQUEST, userController.updateProfile(1L, "Bearer " + userToken, longAddr).getStatusCode());

        // Invalid dateOfBirth (31/02 or wrong format) -> 400
        UpdateProfileRequest badDob = new UpdateProfileRequest();
        badDob.setDateOfBirth("31/02/2000");
        assertEquals(HttpStatus.BAD_REQUEST, userController.updateProfile(1L, "Bearer " + userToken, badDob).getStatusCode());

        // Invalid gender -> 400
        UpdateProfileRequest badGender = new UpdateProfileRequest();
        badGender.setGender("Robot");
        assertEquals(HttpStatus.BAD_REQUEST, userController.updateProfile(1L, "Bearer " + userToken, badGender).getStatusCode());

        // Google account cannot change email -> 400, but can update phone keeping same email -> 200
        String googleToken = tokenService.generateToken(2L, "USER");
        User googleUser = User.builder()
                .id(2L).firebaseUid("fb-uid-2").username("fb_2").fullName("Google User")
                .email("googleuser@gmail.com").password(PasswordUtils.FIREBASE_PLACEHOLDER).role("USER").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(googleUser));

        UpdateProfileRequest changeGoogleEmail = new UpdateProfileRequest();
        changeGoogleEmail.setEmail("other@gmail.com");
        assertEquals(HttpStatus.BAD_REQUEST, userController.updateProfile(2L, "Bearer " + googleToken, changeGoogleEmail).getStatusCode());

        UpdateProfileRequest keepGoogleEmailAddPhone = new UpdateProfileRequest();
        keepGoogleEmailAddPhone.setEmail("googleuser@gmail.com");
        keepGoogleEmailAddPhone.setPhone("0988776655");
        keepGoogleEmailAddPhone.setDateOfBirth("15/08/2000");
        keepGoogleEmailAddPhone.setGender("Nữ");
        assertEquals(HttpStatus.OK, userController.updateProfile(2L, "Bearer " + googleToken, keepGoogleEmailAddPhone).getStatusCode());
        verify(userRepository, never()).findAll();
    }

    @Test
    void addressBookCrudSyncsDefaultAddressAndBlocksIdor() {
        String user1Token = tokenService.generateToken(1L, "USER");
        User user1 = User.builder()
                .id(1L).username("user1").fullName("Nguyễn Văn A")
                .email("user1@gmail.com").phone("0901234567").role("USER").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        java.util.List<UserAddress> store = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicLong idSeq = new java.util.concurrent.atomic.AtomicLong(10L);
        when(userAddressRepository.findByUserIdOrderByDefaultAddressDescIdDesc(1L)).thenAnswer(inv -> new java.util.ArrayList<>(store));
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress addr = inv.getArgument(0);
            if (addr.getId() == null) {
                addr.setId(idSeq.incrementAndGet());
                store.add(addr);
            }
            return addr;
        });
        when(userAddressRepository.findByIdAndUserId(anyLong(), eq(1L))).thenAnswer(inv -> {
            Long reqId = inv.getArgument(0);
            return store.stream().filter(a -> reqId.equals(a.getId())).findFirst();
        });
        doAnswer(inv -> {
            UserAddress addr = inv.getArgument(0);
            store.removeIf(a -> a.getId().equals(addr.getId()));
            return null;
        }).when(userAddressRepository).delete(any(UserAddress.class));

        // IDOR: User 1 cannot access or mutate User 2's address book
        assertEquals(HttpStatus.FORBIDDEN,
                userController.getUserAddresses(2L, "Bearer " + user1Token).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                userController.addUserAddress(2L, "Bearer " + user1Token,
                        new UserAddressRequest("Hacker", "0901234567", "123 ABC", "🏠 Nhà riêng", true)).getStatusCode());

        // Validation: invalid phone -> 400
        assertEquals(HttpStatus.BAD_REQUEST,
                userController.addUserAddress(1L, "Bearer " + user1Token,
                        new UserAddressRequest("Nguyễn Văn A", "12345", "Địa chỉ 1", "🏠 Nhà riêng", true)).getStatusCode());

        // Add 1st address -> automatically becomes default & syncs users.address
        var add1 = userController.addUserAddress(1L, "Bearer " + user1Token,
                new UserAddressRequest("Nguyễn Văn A", "+84901234567", "Địa chỉ 1", "🏠 Nhà riêng", false));
        assertEquals(HttpStatus.CREATED, add1.getStatusCode());
        assertEquals("Địa chỉ 1", user1.getAddress());
        assertEquals(1, store.size());
        assertEquals("0901234567", store.get(0).getPhone());
        org.junit.jupiter.api.Assertions.assertTrue(store.get(0).isDefaultAddress());

        // Add 2nd address marked as default -> demotes 1st & updates users.address
        var add2 = userController.addUserAddress(1L, "Bearer " + user1Token,
                new UserAddressRequest("Trần Văn B", "0987654321", "Địa chỉ 2", "🏢 Văn phòng", true));
        assertEquals(HttpStatus.CREATED, add2.getStatusCode());
        assertEquals("Địa chỉ 2", user1.getAddress());
        org.junit.jupiter.api.Assertions.assertFalse(store.get(0).isDefaultAddress());
        org.junit.jupiter.api.Assertions.assertTrue(store.get(1).isDefaultAddress());

        // Delete default (2nd) address -> promotes 1st to default & syncs users.address
        Long secondId = store.get(1).getId();
        var del2 = userController.deleteUserAddress(1L, secondId, "Bearer " + user1Token);
        assertEquals(HttpStatus.OK, del2.getStatusCode());
        assertEquals(1, store.size());
        org.junit.jupiter.api.Assertions.assertTrue(store.get(0).isDefaultAddress());
        assertEquals("Địa chỉ 1", user1.getAddress());

        // Delete remaining address -> users.address becomes null
        Long firstId = store.get(0).getId();
        var del1 = userController.deleteUserAddress(1L, firstId, "Bearer " + user1Token);
        assertEquals(HttpStatus.OK, del1.getStatusCode());
        assertEquals(0, store.size());
        org.junit.jupiter.api.Assertions.assertNull(user1.getAddress());
    }

    @Test
    void getUserAddressesMigratesLegacyAddressAndProfileUpdateSyncsDefault() {
        String user1Token = tokenService.generateToken(1L, "USER");
        User legacyUser = User.builder()
                .id(1L).username("nguyenvana").fullName("Nguyễn Văn A")
                .email("vana@artifex.vn").phone("0901234567")
                .address("Khu phố 6, Thủ Đức").role("USER").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(legacyUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        java.util.List<UserAddress> store = new java.util.ArrayList<>();
        when(userAddressRepository.findByUserIdOrderByDefaultAddressDescIdDesc(1L)).thenAnswer(inv -> new java.util.ArrayList<>(store));
        when(userAddressRepository.save(any(UserAddress.class))).thenAnswer(inv -> {
            UserAddress addr = inv.getArgument(0);
            if (addr.getId() == null) {
                addr.setId(50L);
                store.add(addr);
            }
            return addr;
        });

        var listResp = userController.getUserAddresses(1L, "Bearer " + user1Token);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> addresses = (java.util.List<Map<String, Object>>) listResp.getBody().get("addresses");
        assertEquals(1, addresses.size());
        assertEquals("Khu phố 6, Thủ Đức", addresses.get(0).get("addressLine"));
        assertEquals(true, addresses.get(0).get("isDefault"));

        UpdateProfileRequest profileUpdate = new UpdateProfileRequest();
        profileUpdate.setAddress("92 Cách Mạng Tháng Tám, Q.1");
        var updResp = userController.updateProfile(1L, "Bearer " + user1Token, profileUpdate);
        assertEquals(HttpStatus.OK, updResp.getStatusCode());
        assertEquals("92 Cách Mạng Tháng Tám, Q.1", store.get(0).getAddressLine());
    }
}
