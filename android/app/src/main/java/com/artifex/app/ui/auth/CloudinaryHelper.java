package com.artifex.app.ui.auth;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudinaryHelper {

    // Cloud Name và Unsigned Preset từ tài khoản Cloudinary thật của bạn:
    public static final String CLOUD_NAME = "wpheip7x";
    public static final String UPLOAD_PRESET = "artifex_avatar";

    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onError(String error);
    }

    /**
     * Chuyển đổi Uri ảnh thành MultipartBody.Part để gửi trực tiếp lên Backend Spring Boot.
     */
    public static MultipartBody.Part prepareFilePart(Context context, Uri imageUri, String partName) {
        if (context == null || imageUri == null) return null;
        try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
             ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream()) {

            if (inputStream == null) return null;

            byte[] buffer = new byte[2048];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byte[] imageBytes = byteBuffer.toByteArray();
            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), imageBytes);
            return MultipartBody.Part.createFormData(partName, "avatar.jpg", requestFile);
        } catch (Exception e) {
            return null;
        }
    }

    public static void uploadImage(Context context, Uri imageUri, UploadCallback callback) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try (InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
                 ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream()) {

                if (inputStream == null) {
                    mainHandler.post(() -> callback.onError("Không thể mở tệp ảnh!"));
                    return;
                }

                byte[] buffer = new byte[2048];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                byte[] imageBytes = byteBuffer.toByteArray();

                OkHttpClient client = new OkHttpClient();
                RequestBody fileBody = RequestBody.create(MediaType.parse("image/*"), imageBytes);
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "avatar.jpg", fileBody)
                        .addFormDataPart("upload_preset", UPLOAD_PRESET)
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String jsonData = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonData);
                        String secureUrl = jsonObject.getString("secure_url");
                        mainHandler.post(() -> callback.onSuccess(secureUrl));
                    } else {
                        String errorDetails = "";
                        if (response.body() != null) {
                            try {
                                errorDetails = response.body().string();
                            } catch (Exception ignored) {}
                        }
                        final String finalErrMsg = !errorDetails.isEmpty()
                                ? "Upload Cloudinary thất bại (" + response.code() + "): " + errorDetails
                                : "Upload ảnh lên Cloudinary thất bại (" + response.code() + ")!";
                        mainHandler.post(() -> callback.onError(finalErrMsg));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Lỗi upload: " + e.getMessage()));
            }
        }).start();
    }
}
