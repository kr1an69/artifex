package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.*;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhoneAuthTest {
    private UserRepository repository;
    private AuthController controller;

    @BeforeEach
    void setup() {
        repository = mock(UserRepository.class);
        controller = new AuthController(repository, mock(com.artifex.backend.service.FirebaseLoginService.class), new com.artifex.backend.service.TokenService());
    }

    private User existingUser() {
        return User.builder().id(1L).username("legacy_name")
                .phone("0912345678").password("secret123")
                .email("customer@gmail.com").fullName("Khách hàng").build();
    }

    @Test
    void existingAccountCanLoginWithInternationalPhone() {
        when(repository.findAll()).thenReturn(List.of(existingUser()));
        var response = controller.login(new LoginRequest("+84 912 345 678", "secret123"));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, ((User) response.getBody().get("user")).getId());
    }

    @Test
    void usernameAndEmailAreNotAcceptedForLogin() {
        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.login(new LoginRequest("legacy_name", "secret123")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.login(new LoginRequest("customer@gmail.com", "secret123")).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void wrongPasswordAndGooglePlaceholderCannotLogin() {
        User user = existingUser();
        when(repository.findAll()).thenReturn(List.of(user));
        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.login(new LoginRequest("0912345678", "wrong")).getStatusCode());
        user.setPassword("FIREBASE_AUTH");
        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.login(new LoginRequest("0912345678", "FIREBASE_AUTH")).getStatusCode());
    }

    @Test
    void unknownPhoneResetDoesNotCreateAccount() {
        when(repository.findAll()).thenReturn(List.of());
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.requestForgotPasswordOtp(new ForgotPasswordOtpRequest("0912345678")).getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void duplicatePhoneCannotRegisterWithDifferentFormat() {
        when(repository.findAll()).thenReturn(List.of(existingUser()));
        var request = new RegisterRequest("new@gmail.com", "secret123", "New customer",
                "+84912345678", null, "123456");
        assertEquals(HttpStatus.BAD_REQUEST, controller.register(request).getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void registrationNeedsOtpButNoUsername() throws Exception {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new RegisterRequest("new@gmail.com", "secret123", "New customer",
                "+84912345678", null, "123456");
        assertEquals(HttpStatus.BAD_REQUEST, controller.register(request).getStatusCode());
        seedOtp("reg:0912345678", "123456");
        var response = controller.register(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        User user = (User) response.getBody().get("user");
        assertEquals("0912345678", user.getPhone());
        assertNotNull(user.getUsername()); // Legacy database column, generated internally.
    }

    @Test
    void resetOtpIsBoundToPhoneAndConsumed() throws Exception {
        User user = existingUser();
        when(repository.findAll()).thenReturn(List.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        seedOtp("reset:+84912345678", "123456");
        assertEquals(HttpStatus.BAD_REQUEST, controller.resetPasswordWithOtp(
                new ForgotPasswordRequest("0912345678", "654321", "newpass123")).getStatusCode());
        assertEquals("secret123", user.getPassword());
        var request = new ForgotPasswordRequest("+84912345678", "123456", "newpass123");
        assertEquals(HttpStatus.OK, controller.resetPasswordWithOtp(request).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.resetPasswordWithOtp(request).getStatusCode());
        verify(repository, times(1)).save(user);
    }

    @SuppressWarnings("unchecked")
    private void seedOtp(String key, String code) throws Exception {
        Field field = AuthController.class.getDeclaredField("otpStorage");
        field.setAccessible(true);
        Class<?> type = Class.forName(AuthController.class.getName() + "$OtpRecord");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, long.class);
        constructor.setAccessible(true);
        ((Map<String, Object>) field.get(controller)).put(key,
                constructor.newInstance(code, System.currentTimeMillis() + 60000));
    }
}
