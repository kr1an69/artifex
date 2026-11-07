package com.artifex.backend.controller;

import com.artifex.backend.dto.AuthDTO.FirebaseLoginRequest;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.UserRepository;
import com.artifex.backend.service.FirebaseLoginService;
import com.artifex.backend.service.FirebaseTokenVerifier;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class FirebaseLoginTest {
    private UserRepository users;
    private FirebaseTokenVerifier verifier;
    private FirebaseLoginService service;

    @BeforeEach
    void setup() {
        users = mock(UserRepository.class);
        verifier = mock(FirebaseTokenVerifier.class);
        service = new FirebaseLoginService(users, verifier, new com.artifex.backend.service.TokenService());
    }

    private FirebaseToken verifiedToken(String uid, String email) {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(uid);
        when(token.getEmail()).thenReturn(email);
        when(token.isEmailVerified()).thenReturn(true);
        when(verifier.verify("signed-token")).thenReturn(token);
        return token;
    }

    @Test
    void missingTokenCannotUseLegacyEmailLogin() {
        var realVerifier = new FirebaseTokenVerifier("");
        var tokenService = new com.artifex.backend.service.TokenService();
        var controller = new AuthController(users, new FirebaseLoginService(users, realVerifier, tokenService), tokenService);
        var response = controller.firebaseLogin(new FirebaseLoginRequest(null));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(users);
    }

    @Test
    void invalidExpiredOrRevokedTokenCannotReadUsers() {
        when(verifier.verify("bad-token")).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var controller = new AuthController(users, service, new com.artifex.backend.service.TokenService());
        assertEquals(HttpStatus.UNAUTHORIZED,
                controller.firebaseLogin(new FirebaseLoginRequest("bad-token")).getStatusCode());
        verifyNoInteractions(users);
    }

    @Test
    void missingServerConfigurationFailsClosed() {
        var tokenService = new com.artifex.backend.service.TokenService();
        var controller = new AuthController(users, new FirebaseLoginService(users, new FirebaseTokenVerifier(""), tokenService), tokenService);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                controller.firebaseLogin(new FirebaseLoginRequest("some-token")).getStatusCode());
        verifyNoInteractions(users);
    }

    @Test
    void unverifiedEmailCannotCreateOrLinkAccount() {
        FirebaseToken token = verifiedToken("uid", "person@gmail.com");
        when(token.isEmailVerified()).thenReturn(false);
        assertEquals(HttpStatus.UNAUTHORIZED,
                assertThrows(ResponseStatusException.class, () -> service.login("signed-token")).getStatusCode());
        verifyNoInteractions(users);
    }

    @Test
    void verifiedUidCreatesAccountFromTrustedClaimsOnly() {
        FirebaseToken token = verifiedToken("trusted-uid", "Person@gmail.com");
        when(token.getName()).thenReturn("Verified Name");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            assertEquals("trusted-uid", saved.getFirebaseUid());
            assertEquals("person@gmail.com", saved.getEmail());
            assertEquals("Verified Name", saved.getFullName());
            assertEquals("USER", saved.getRole());
            saved.setId(42L);
            return saved;
        });
        Map<?, ?> profile = (Map<?, ?>) service.login("signed-token").get("user");
        assertEquals(42L, profile.get("id"));
        assertFalse(profile.containsKey("password"));
    }

    @Test
    void existingUidRemainsSameAccountEvenWhenTokenEmailChanges() {
        verifiedToken("existing-uid", "changed@gmail.com");
        User existing = User.builder().id(7L).firebaseUid("existing-uid")
                .email("original@gmail.com").password("FIREBASE_AUTH").build();
        when(users.findByFirebaseUid("existing-uid")).thenReturn(Optional.of(existing));
        Map<?, ?> profile = (Map<?, ?>) service.login("signed-token").get("user");
        assertEquals(7L, profile.get("id"));
        assertEquals("FIREBASE_AUTH", existing.getPassword());
        verify(users, never()).existsByEmail(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void matchingEmailNeverTakesOverLocalOrOtherFirebaseAccount() {
        verifiedToken("different-uid", "existing@gmail.com");
        when(users.existsByEmail("existing@gmail.com")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> service.login("signed-token")).getStatusCode());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void caseInsensitiveDatabaseMatchCannotTakeOverAnotherUid() {
        verifiedToken("UID", "person@gmail.com");
        when(users.findByFirebaseUid("UID")).thenReturn(Optional.of(
                User.builder().id(9L).firebaseUid("uid").build()));
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(ResponseStatusException.class, () -> service.login("signed-token")).getStatusCode());
        verify(users, never()).saveAndFlush(any());
    }
}
