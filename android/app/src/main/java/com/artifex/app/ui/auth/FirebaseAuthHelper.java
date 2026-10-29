package com.artifex.app.ui.auth;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper quản lý khởi tạo và xác thực Firebase Authentication & Google OAuth thật cho Bạn 1 (package ui/auth).
 */
public class FirebaseAuthHelper {

    // =========================================================================================
    // ĐIỀN 3 THÔNG SỐ TỪ FIREBASE CONSOLE (Project Settings -> General) ĐỂ MỞ GOOGLE LOGIN THẬT:
    // (Hoặc copy thông số từ file google-services.json vào 3 dòng dưới đây)
    // =========================================================================================
    public static final String FIREBASE_API_KEY = "";      // "current_key" trong google-services.json (bắt đầu bằng "AIzaSy...")
    public static final String FIREBASE_APP_ID = "";       // "mobilesdk_app_id" (bắt đầu bằng "1:...:android:...")
    public static final String FIREBASE_PROJECT_ID = "";   // "project_id" (ví dụ: "artifex-pc-store")

    public interface FirebaseAuthCallback {
        void onSuccess(String firebaseUid, String email, String fullName, String avatarUrl, boolean isRealFirebaseSession);
        void onError(String errorMessage);
    }

    public interface ResetPasswordCallback {
        void onSuccess(String message);
        void onError(String errorMessage);
    }

    /**
     * Kiểm tra xem dự án đã được cấu hình khóa Firebase thật hay chưa.
     */
    public static boolean isFirebaseConfigured(Context context) {
        return getFirebaseAuth(context) != null;
    }

    /**
     * Khởi tạo và lấy đối tượng FirebaseAuth an toàn.
     */
    public static FirebaseAuth getFirebaseAuth(Context context) {
        if (context == null) return null;
        Context appContext = context.getApplicationContext();
        try {
            if (!FirebaseApp.getApps(appContext).isEmpty()) {
                return FirebaseAuth.getInstance();
            }
            // 1. Thử khởi tạo mặc định nếu có google-services resource
            FirebaseApp defaultApp = FirebaseApp.initializeApp(appContext);
            if (defaultApp != null) {
                return FirebaseAuth.getInstance();
            }
            // 2. Khởi tạo trực tiếp từ 3 hằng số cấu hình ở trên (không cần sửa build.gradle.kts)
            if (!FIREBASE_API_KEY.trim().isEmpty()
                    && !FIREBASE_APP_ID.trim().isEmpty()
                    && !FIREBASE_PROJECT_ID.trim().isEmpty()) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApiKey(FIREBASE_API_KEY.trim())
                        .setApplicationId(FIREBASE_APP_ID.trim())
                        .setProjectId(FIREBASE_PROJECT_ID.trim())
                        .build();
                FirebaseApp.initializeApp(appContext, options);
                return FirebaseAuth.getInstance();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Mở trực tiếp màn hình Đăng nhập Google thật (accounts.google.com) thông qua Firebase OAuthProvider ("google.com").
     * Không cần thêm thư viện ngoài vào build.gradle.kts, sử dụng trực tiếp firebase-auth có sẵn.
     */
    public static void signInWithRealGooglePopup(Activity activity, FirebaseAuthCallback callback) {
        FirebaseAuth mAuth = getFirebaseAuth(activity);
        if (mAuth == null) {
            callback.onError("NEED_FIREBASE_CONFIG");
            return;
        }

        OAuthProvider.Builder provider = OAuthProvider.newBuilder("google.com");
        // Luôn hiển thị màn hình chọn tài khoản Google thật
        provider.addCustomParameter("prompt", "select_account");
        List<String> scopes = new ArrayList<>();
        scopes.add("email");
        scopes.add("profile");
        provider.setScopes(scopes);

        Task<AuthResult> pendingResultTask = mAuth.getPendingAuthResult();
        if (pendingResultTask != null) {
            pendingResultTask
                    .addOnSuccessListener(authResult -> extractAndNotifyGoogleUser(authResult.getUser(), callback))
                    .addOnFailureListener(e -> callback.onError("Lỗi xác thực Google: " + e.getMessage()));
        } else {
            mAuth.startActivityForSignInWithProvider(activity, provider.build())
                    .addOnSuccessListener(authResult -> extractAndNotifyGoogleUser(authResult.getUser(), callback))
                    .addOnFailureListener(e -> callback.onError("Đăng nhập Google thất bại hoặc đã hủy: " + e.getMessage()));
        }
    }

    private static void extractAndNotifyGoogleUser(FirebaseUser fbUser, FirebaseAuthCallback callback) {
        if (fbUser == null || fbUser.getEmail() == null) {
            callback.onError("Không lấy được thông tin Email từ tài khoản Google!");
            return;
        }
        String email = fbUser.getEmail();
        String name = (fbUser.getDisplayName() != null && !fbUser.getDisplayName().isEmpty())
                ? fbUser.getDisplayName()
                : email.split("@")[0];
        String photo = fbUser.getPhotoUrl() != null ? fbUser.getPhotoUrl().toString() : "";
        callback.onSuccess(fbUser.getUid(), email, name, photo, true);
    }

    /**
     * Xác thực trực tiếp với Firebase Authentication bằng Email/Password (Đăng nhập, hoặc tự động Tạo mới trên Firebase).
     */
    public static void authenticateOrRegisterWithFirebase(
            Context context,
            String email,
            String password,
            String fullName,
            FirebaseAuthCallback callback) {

        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        String cleanPass = password != null ? password.trim() : "";
        String cleanName = (fullName != null && !fullName.trim().isEmpty())
                ? fullName.trim()
                : (cleanEmail.contains("@") ? cleanEmail.split("@")[0] : "Khách hàng Artifex");

        FirebaseAuth mAuth = getFirebaseAuth(context);

        if (mAuth == null) {
            String fallbackUid = "firebase_uid_" + Math.abs(cleanEmail.hashCode());
            callback.onSuccess(fallbackUid, cleanEmail, cleanName, "", false);
            return;
        }

        mAuth.signInWithEmailAndPassword(cleanEmail, cleanPass)
                .addOnCompleteListener(signInTask -> {
                    if (signInTask.isSuccessful() && mAuth.getCurrentUser() != null) {
                        FirebaseUser fbUser = mAuth.getCurrentUser();
                        String resolvedName = (fbUser.getDisplayName() != null && !fbUser.getDisplayName().isEmpty())
                                ? fbUser.getDisplayName()
                                : cleanName;
                        String resolvedAvatar = fbUser.getPhotoUrl() != null ? fbUser.getPhotoUrl().toString() : "";
                        callback.onSuccess(fbUser.getUid(), fbUser.getEmail(), resolvedName, resolvedAvatar, true);
                    } else {
                        Exception signInEx = signInTask.getException();
                        if (signInEx instanceof FirebaseAuthInvalidUserException
                                || (signInEx != null && signInEx.getMessage() != null
                                && (signInEx.getMessage().contains("no user record")
                                || signInEx.getMessage().contains("INVALID_LOGIN_CREDENTIALS")))) {

                            mAuth.createUserWithEmailAndPassword(cleanEmail, cleanPass)
                                    .addOnCompleteListener(createTask -> {
                                        if (createTask.isSuccessful() && mAuth.getCurrentUser() != null) {
                                            FirebaseUser newUser = mAuth.getCurrentUser();
                                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                                    .setDisplayName(cleanName)
                                                    .build();
                                            newUser.updateProfile(profileUpdates);
                                            String photo = newUser.getPhotoUrl() != null ? newUser.getPhotoUrl().toString() : "";
                                            callback.onSuccess(newUser.getUid(), newUser.getEmail(), cleanName, photo, true);
                                        } else {
                                            String errMsg = createTask.getException() != null
                                                    ? createTask.getException().getMessage()
                                                    : "Xác thực Firebase thất bại!";
                                            callback.onError("Lỗi Firebase Auth: " + errMsg);
                                        }
                                    });
                        } else {
                            String errMsg = signInEx != null ? signInEx.getMessage() : "Sai thông tin xác thực Firebase!";
                            callback.onError("Lỗi Firebase Auth: " + errMsg);
                        }
                    }
                });
    }

    /**
     * Đồng bộ ngầm tài khoản lên Firebase Authentication khi người dùng Đăng ký hoặc Đăng nhập bằng tài khoản MySQL.
     */
    public static void syncUserToFirebaseSilently(Context context, String email, String password, String fullName) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) return;
        if (password == null || password.trim().length() < 6) return;

        FirebaseAuth mAuth = getFirebaseAuth(context);
        if (mAuth == null) return;

        String cleanEmail = email.trim().toLowerCase();
        String cleanPass = password.trim();
        String cleanName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : cleanEmail.split("@")[0];

        mAuth.signInWithEmailAndPassword(cleanEmail, cleanPass)
                .addOnFailureListener(e -> {
                    mAuth.createUserWithEmailAndPassword(cleanEmail, cleanPass)
                            .addOnSuccessListener(authResult -> {
                                FirebaseUser user = authResult.getUser();
                                if (user != null) {
                                    UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                                            .setDisplayName(cleanName)
                                            .build();
                                    user.updateProfile(req);
                                }
                            });
                });
    }

    /**
     * Gửi email đặt lại mật khẩu qua Firebase Authentication.
     */
    public static void sendPasswordResetEmail(Context context, String email, ResetPasswordCallback callback) {
        String cleanEmail = email != null ? email.trim().toLowerCase() : "";
        if (cleanEmail.isEmpty()) {
            callback.onError("Vui lòng nhập địa chỉ Email!");
            return;
        }

        FirebaseAuth mAuth = getFirebaseAuth(context);
        if (mAuth == null) {
            callback.onError("Chưa điền khóa FIREBASE_API_KEY trong FirebaseAuthHelper! Bạn có thể dùng chức năng Đặt lại mật khẩu bên dưới.");
            return;
        }

        mAuth.sendPasswordResetEmail(cleanEmail)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess("Đã gửi link khôi phục mật khẩu từ Firebase tới email: " + cleanEmail);
                    } else {
                        String err = task.getException() != null ? task.getException().getMessage() : "Không thể gửi email!";
                        callback.onError("Firebase: " + err);
                    }
                });
    }
}
