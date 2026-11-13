package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.ChangePasswordRequest;
import com.artifex.backend.dto.AuthDTO.UpdateAvatarRequest;
import com.artifex.backend.dto.AuthDTO.UpdateProfileRequest;
import com.artifex.backend.entity.User;
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
    private TokenService tokenService;
    private CloudinaryService cloudinaryService;
    private UserController userController;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        tokenService = new TokenService();
        cloudinaryService = mock(CloudinaryService.class);
        userController = new UserController(userRepository, tokenService, cloudinaryService);
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
    void cloudinaryServiceRequiresSignedCredentialsAndNeverFallsBackToUnsigned() throws Exception {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", jpegBytes);

        com.cloudinary.Cloudinary missingCreds = new com.cloudinary.Cloudinary(
                com.cloudinary.utils.ObjectUtils.asMap("cloud_name", "wpheip7x", "api_key", "", "api_secret", "")
        );
        CloudinaryService unconfiguredService = new CloudinaryService(missingCreds);
        org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class,
                () -> unconfiguredService.uploadImage(file, "artifex/avatars")
        );

        com.cloudinary.Cloudinary demoCreds = new com.cloudinary.Cloudinary(
                com.cloudinary.utils.ObjectUtils.asMap("cloud_name", "demo", "api_key", "demo", "api_secret", "demo")
        );
        CloudinaryService demoService = new CloudinaryService(demoCreds);
        org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class,
                () -> demoService.uploadImage(file, "artifex/avatars")
        );

        com.cloudinary.Cloudinary spyCloudinary = org.mockito.Mockito.spy(new com.cloudinary.Cloudinary(
                com.cloudinary.utils.ObjectUtils.asMap("cloud_name", "wpheip7x", "api_key", "test_key", "api_secret", "test_secret")
        ));
        com.cloudinary.Uploader mockUploader = org.mockito.Mockito.mock(com.cloudinary.Uploader.class);
        org.mockito.Mockito.doReturn(mockUploader).when(spyCloudinary).uploader();
        when(mockUploader.upload(any(), any())).thenThrow(new java.io.IOException("signed upload failed"));

        CloudinaryService failingSignedService = new CloudinaryService(spyCloudinary);
        org.junit.jupiter.api.Assertions.assertThrows(
                java.io.IOException.class,
                () -> failingSignedService.uploadImage(file, "artifex/avatars")
        );

        verify(mockUploader).upload(any(), any());
        org.mockito.Mockito.verifyNoMoreInteractions(mockUploader);
    }
}
