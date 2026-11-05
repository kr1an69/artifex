package com.artifex.app.ui.auth;

import android.app.Activity;
import android.content.Context;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/** Google credential -> Firebase session -> Firebase ID token. No simulated identities. */
public final class FirebaseAuthHelper {
    private FirebaseAuthHelper() {}

    public interface FirebaseAuthCallback {
        void onSuccess(String idToken);
        void onError(String message);
    }

    public static FirebaseAuth getFirebaseAuth(Context context) {
        if (context == null) return null;
        try {
            Context app = context.getApplicationContext();
            if (FirebaseApp.getApps(app).isEmpty() && FirebaseApp.initializeApp(app) == null) return null;
            return FirebaseAuth.getInstance();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return null;
        }
    }

    public static void signInWithGoogle(Activity activity, CancellationSignal cancellation,
                                        FirebaseAuthCallback callback) {
        FirebaseAuth auth = getFirebaseAuth(activity);
        int resource = activity.getResources().getIdentifier(
                "default_web_client_id", "string", activity.getPackageName());
        if (auth == null || resource == 0 || activity.getString(resource).trim().isEmpty()) {
            callback.onError("Đăng nhập Google chưa được cấu hình. Vui lòng liên hệ nhóm phát triển.");
            return;
        }
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setServerClientId(activity.getString(resource))
                .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, cancellation,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        if (cancellation.isCanceled()) return;
                        Credential credential = result.getCredential();
                        if (!(credential instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            callback.onError("Không nhận được thông tin xác thực Google hợp lệ.");
                            return;
                        }
                        try {
                            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
                            auth.signInWithCredential(GoogleAuthProvider.getCredential(google.getIdToken(), null))
                                    .addOnSuccessListener(resultAuth -> {
                                        if (cancellation.isCanceled()) return;
                                        FirebaseUser user = resultAuth.getUser();
                                        if (user == null) {
                                            callback.onError("Firebase không trả về tài khoản hợp lệ.");
                                            return;
                                        }
                                        user.getIdToken(true).addOnSuccessListener(token -> {
                                            if (cancellation.isCanceled()) return;
                                            if (token.getToken() == null || token.getToken().isEmpty()) {
                                                callback.onError("Không lấy được mã xác thực Firebase.");
                                            } else {
                                                callback.onSuccess(token.getToken());
                                            }
                                        }).addOnFailureListener(e -> {
                                            if (!cancellation.isCanceled()) callback.onError("Không lấy được mã xác thực Firebase. Vui lòng thử lại.");
                                        });
                                    }).addOnFailureListener(e -> {
                                        if (!cancellation.isCanceled()) callback.onError("Xác thực Firebase thất bại. Vui lòng thử lại.");
                                    });
                        } catch (Exception e) {
                            callback.onError("Thông tin xác thực Google không hợp lệ.");
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (!cancellation.isCanceled()) callback.onError("Đăng nhập Google đã hủy hoặc không khả dụng. Vui lòng thử lại.");
                    }
                });
    }
}
