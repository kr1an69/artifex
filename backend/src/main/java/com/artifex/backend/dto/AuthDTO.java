package com.artifex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        private String phone;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterRequest {
        private String email;
        private String password;
        private String fullName;
        private String phone;
        private String address;
        private String otp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterOtpRequest {
        private String phone;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FirebaseLoginRequest {
        private String idToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfileRequest {
        private String fullName;
        private String email;
        private String phone;
        private String address;
        private String dateOfBirth;
        private String gender;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateAvatarRequest {
        private String avatarUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForgotPasswordOtpRequest {
        private String phone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForgotPasswordRequest {
        private String phone;
        private String otp;
        private String newPassword;
    }
}
