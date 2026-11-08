package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.ChangePasswordRequest;
import com.artifex.backend.dto.AuthDTO.UpdateProfileRequest;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.UserRepository;
import com.artifex.backend.service.PasswordUtils;
import com.artifex.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserAuthorizationTest {

    private UserRepository userRepository;
    private TokenService tokenService;
    private UserController userController;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        tokenService = new TokenService();
        userController = new UserController(userRepository, tokenService);
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
}
