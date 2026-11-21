package com.artifex.app.ui.auth;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudinaryHelper {

    // Cloud Name và Unsigned Preset phục vụ fallback / upload trực tiếp từ các màn hình dùng chung
    public static final String CLOUD_NAME = "wpheip7x";
    public static final String UPLOAD_PRESET = "artifex_avatar";

    private static final int MAX_AVATAR_DIMENSION_PX = 1024;
    private static final int MAX_AVATAR_BYTES = 900 * 1024;
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");

    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onError(String error);
    }

    public static final class PrepareResult {
        public final MultipartBody.Part part;
        public final String errorMessage;

        public PrepareResult(MultipartBody.Part part, String errorMessage) {
            this.part = part;
            this.errorMessage = errorMessage;
        }
    }

    private static final class CompressResult {
        final byte[] bytes;
        final String errorMessage;

        CompressResult(byte[] bytes, String errorMessage) {
            this.bytes = bytes;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Thu gọn, xoay đúng hướng EXIF và nén ảnh về định dạng JPEG (<= 1024px, ~<= 900KB),
     * phân biệt rõ từng nguyên nhân lỗi (thiếu quyền/không tìm thấy tệp, sai định dạng/ảnh hỏng, tràn bộ nhớ, lỗi I/O).
     */
    private static CompressResult compressImageForAvatar(Context context, Uri imageUri) {
        if (context == null || imageUri == null) {
            return new CompressResult(null, "Không thể mở hoặc đọc tệp ảnh đã chọn (tệp không tồn tại hoặc thiếu quyền truy cập)!");
        }
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream boundsStream = context.getContentResolver().openInputStream(imageUri)) {
                if (boundsStream == null) {
                    return new CompressResult(null, "Không thể mở hoặc đọc tệp ảnh đã chọn (tệp không tồn tại hoặc thiếu quyền truy cập)!");
                }
                BitmapFactory.decodeStream(boundsStream, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return new CompressResult(null, "Định dạng tệp không được hỗ trợ hoặc ảnh bị hỏng (chỉ hỗ trợ JPG, PNG, WEBP)!");
            }

            int sampleSize = 1;
            while ((bounds.outWidth / (sampleSize * 2)) >= MAX_AVATAR_DIMENSION_PX
                    || (bounds.outHeight / (sampleSize * 2)) >= MAX_AVATAR_DIMENSION_PX) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = sampleSize;
            try (InputStream decodeStream = context.getContentResolver().openInputStream(imageUri)) {
                if (decodeStream == null) {
                    return new CompressResult(null, "Không thể mở hoặc đọc tệp ảnh đã chọn (tệp không tồn tại hoặc thiếu quyền truy cập)!");
                }
                bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOpts);
            }
            if (bitmap == null) {
                return new CompressResult(null, "Định dạng tệp không được hỗ trợ hoặc ảnh bị hỏng (chỉ hỗ trợ JPG, PNG, WEBP)!");
            }

            int rotationDegrees = readExifRotationDegrees(context, imageUri);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int maxDim = Math.max(width, height);
            float scale = maxDim > MAX_AVATAR_DIMENSION_PX
                    ? (float) MAX_AVATAR_DIMENSION_PX / maxDim
                    : 1f;

            if (scale < 1f || rotationDegrees != 0) {
                Matrix matrix = new Matrix();
                if (scale < 1f) {
                    matrix.postScale(scale, scale);
                }
                if (rotationDegrees != 0) {
                    matrix.postRotate(rotationDegrees);
                }
                Bitmap transformed = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                if (transformed != bitmap) {
                    bitmap.recycle();
                    bitmap = transformed;
                }
            }

            // Nền trắng khi chuyển ảnh PNG có kênh trong suốt sang JPEG để tránh viền đen
            if (bitmap.hasAlpha()) {
                Bitmap opaque = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(opaque);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(bitmap, 0f, 0f, null);
                bitmap.recycle();
                bitmap = opaque;
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int quality = 85;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
            while (output.size() > MAX_AVATAR_BYTES && quality > 55) {
                output.reset();
                quality -= 10;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
            }
            return new CompressResult(output.toByteArray(), null);
        } catch (FileNotFoundException | SecurityException e) {
            return new CompressResult(null, "Không thể mở hoặc đọc tệp ảnh đã chọn (tệp không tồn tại hoặc thiếu quyền truy cập)!");
        } catch (OutOfMemoryError e) {
            return new CompressResult(null, "Ảnh có độ phân giải quá lớn, thiết bị không đủ bộ nhớ để xử lý!");
        } catch (IOException e) {
            return new CompressResult(null, "Lỗi đọc dữ liệu tệp ảnh: " + e.getMessage());
        } catch (Exception e) {
            return new CompressResult(null, "Lỗi đọc dữ liệu tệp ảnh: " + e.getMessage());
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private static int readExifRotationDegrees(Context context, Uri imageUri) {
        try (InputStream exifStream = context.getContentResolver().openInputStream(imageUri)) {
            if (exifStream == null) return 0;
            ExifInterface exif = new ExifInterface(exifStream);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * Chuyển đổi Uri ảnh thành MultipartBody.Part JPEG đã nén kèm thông báo lỗi chi tiết nếu thất bại.
     */
    public static PrepareResult prepareFilePartWithResult(Context context, Uri imageUri, String partName) {
        CompressResult result = compressImageForAvatar(context, imageUri);
        if (result.bytes == null || result.bytes.length == 0) {
            String msg = (result.errorMessage != null && !result.errorMessage.isEmpty())
                    ? result.errorMessage
                    : "Định dạng tệp không được hỗ trợ hoặc ảnh bị hỏng (chỉ hỗ trợ JPG, PNG, WEBP)!";
            return new PrepareResult(null, msg);
        }
        RequestBody requestFile = RequestBody.create(MEDIA_TYPE_JPEG, result.bytes);
        MultipartBody.Part part = MultipartBody.Part.createFormData(partName, "avatar.jpg", requestFile);
        return new PrepareResult(part, null);
    }

    /**
     * Chuyển đổi Uri ảnh thành MultipartBody.Part JPEG đã nén để gửi trực tiếp lên Backend Spring Boot.
     */
    public static MultipartBody.Part prepareFilePart(Context context, Uri imageUri, String partName) {
        return prepareFilePartWithResult(context, imageUri, partName).part;
    }

    /**
     * Upload trực tiếp lên Cloudinary qua Unsigned Preset (giữ tương thích cho CatalogFragment & các màn hình dùng chung).
     */
    public static void uploadImage(Context context, Uri imageUri, UploadCallback callback) {
        if (callback == null) return;
        Handler mainHandler = new Handler(Looper.getMainLooper());
        if (context == null || imageUri == null) {
            mainHandler.post(() -> callback.onError("Không thể mở tệp ảnh!"));
            return;
        }

        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            byte[] imageBytes = null;
            MediaType uploadMediaType = MEDIA_TYPE_JPEG;
            String uploadFilename = "image.jpg";
            CompressResult compressed = compressImageForAvatar(appContext, imageUri);
            if (compressed.bytes != null && compressed.bytes.length > 0) {
                imageBytes = compressed.bytes;
            } else {
                uploadMediaType = MediaType.parse("image/*");
                try (InputStream inputStream = appContext.getContentResolver().openInputStream(imageUri);
                     ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream()) {
                    if (inputStream == null) {
                        String msg = compressed.errorMessage != null ? compressed.errorMessage : "Không thể mở tệp ảnh!";
                        mainHandler.post(() -> callback.onError(msg));
                        return;
                    }
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        byteBuffer.write(buffer, 0, len);
                    }
                    imageBytes = byteBuffer.toByteArray();
                } catch (OutOfMemoryError oom) {
                    mainHandler.post(() -> callback.onError("Ảnh có độ phân giải quá lớn, thiết bị không đủ bộ nhớ để xử lý!"));
                    return;
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Lỗi đọc dữ liệu tệp ảnh: " + e.getMessage()));
                    return;
                }
            }

            if (imageBytes == null || imageBytes.length == 0) {
                mainHandler.post(() -> callback.onError("Tệp ảnh trống hoặc không hợp lệ!"));
                return;
            }

            try {
                OkHttpClient client = new OkHttpClient();
                RequestBody fileBody = RequestBody.create(uploadMediaType, imageBytes);
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", uploadFilename, fileBody)
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
