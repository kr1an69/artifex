package com.artifex.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FirebaseTokenVerifier {
    private final String projectId;
    private FirebaseAuth auth;

    @Value("${GOOGLE_APPLICATION_CREDENTIALS:}")
    private String credentialsPath = "";

    public FirebaseTokenVerifier(@Value("${firebase.project-id:artifex-82a5d}") String projectId) {
        this.projectId = projectId;
    }

    private InputStream openCredentialsStream() throws IOException {
        // 1. Thử tải file từ classpath (resources/firebase-adminsdk.json)
        ClassPathResource cpr = new ClassPathResource("firebase-adminsdk.json");
        if (cpr.exists()) {
            return cpr.getInputStream();
        }

        // 2. Nếu có đường dẫn file cấu hình trong biến môi trường / properties
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            Path directPath = Path.of(credentialsPath.trim());
            if (Files.exists(directPath)) {
                return Files.newInputStream(directPath);
            }
        }

        // 3. Thử các đường dẫn tương đối dự phòng trong cấu trúc monorepo
        String[] fallbackPaths = new String[]{
                "firebase-adminsdk.json",
                "src/main/resources/firebase-adminsdk.json",
                "backend/src/main/resources/firebase-adminsdk.json",
                "../backend/src/main/resources/firebase-adminsdk.json",
                "artifex/backend/src/main/resources/firebase-adminsdk.json"
        };
        for (String relPath : fallbackPaths) {
            File f = new File(relPath);
            if (f.exists() && f.isFile()) {
                return Files.newInputStream(f.toPath());
            }
        }

        return null;
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
            InputStream stream = openCredentialsStream();
            if (stream != null) {
                try (stream) {
                    credentials = GoogleCredentials.fromStream(stream);
                }
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setProjectId(projectId.trim())
                    .setCredentials(credentials).build();

            FirebaseApp firebaseApp;
            try {
                firebaseApp = FirebaseApp.getInstance("artifex-auth");
            } catch (IllegalStateException e) {
                firebaseApp = FirebaseApp.initializeApp(options, "artifex-auth");
            }
            auth = FirebaseAuth.getInstance(firebaseApp);
            return auth;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Máy chủ chưa sẵn sàng xác thực Firebase: " + e.getMessage());
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
