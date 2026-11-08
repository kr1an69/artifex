package com.artifex.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

@Service
public class FirebaseTokenVerifier {
    private final String projectId;
    private FirebaseAuth auth;

    @Value("${GOOGLE_APPLICATION_CREDENTIALS:}")
    private String credentialsPath = "";

    public FirebaseTokenVerifier(@Value("${firebase.project-id:}") String projectId) {
        this.projectId = projectId;
    }

    // Lazy initialization leaves phone login available without Firebase configuration.
    private synchronized FirebaseAuth getAuth() {
        if (auth != null) return auth;
        if (projectId == null || projectId.isBlank()
                || System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Máy chủ chưa được cấu hình xác thực Firebase thật.");
        }
        try {
            GoogleCredentials credentials;
            if (credentialsPath.isBlank()) {
                credentials = GoogleCredentials.getApplicationDefault();
            } else {
                try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(credentialsPath))) {
                    credentials = GoogleCredentials.fromStream(input);
                }
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setProjectId(projectId.trim())
                    .setCredentials(credentials).build();
            auth = FirebaseAuth.getInstance(FirebaseApp.initializeApp(options, "artifex-auth"));
            return auth;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Máy chủ chưa sẵn sàng xác thực Firebase.");
        }
    }

    public FirebaseToken verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Thiếu Firebase ID token.");
        }
        try {
            // Validate signature, expiration, issuer/audience, revocation and disabled users.
            return getAuth().verifyIdToken(idToken, true);
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Phiên Firebase không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
        }
    }
}
