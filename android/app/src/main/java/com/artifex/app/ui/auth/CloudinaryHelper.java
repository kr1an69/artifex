package com.artifex.app.ui.auth;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class CloudinaryHelper {

    private static final int MAX_AVATAR_DIMENSION_PX = 1024;
    private static final int MAX_AVATAR_BYTES = 900 * 1024;
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");

    /**
     * Thu gọn, xoay đúng hướng EXIF và nén ảnh về định dạng JPEG (<= 1024px, ~<= 900KB).
     */
    private static byte[] compressImageForAvatar(Context context, Uri imageUri) {
        if (context == null || imageUri == null) return null;
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream boundsStream = context.getContentResolver().openInputStream(imageUri)) {
                if (boundsStream == null) return null;
                BitmapFactory.decodeStream(boundsStream, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sampleSize = 1;
            while ((bounds.outWidth / (sampleSize * 2)) >= MAX_AVATAR_DIMENSION_PX
                    || (bounds.outHeight / (sampleSize * 2)) >= MAX_AVATAR_DIMENSION_PX) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = sampleSize;
            try (InputStream decodeStream = context.getContentResolver().openInputStream(imageUri)) {
                if (decodeStream == null) return null;
                bitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOpts);
            }
            if (bitmap == null) {
                return null;
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
            return output.toByteArray();
        } catch (Exception e) {
            return null;
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
     * Chuyển đổi Uri ảnh thành MultipartBody.Part JPEG đã nén để gửi trực tiếp lên Backend Spring Boot.
     */
    public static MultipartBody.Part prepareFilePart(Context context, Uri imageUri, String partName) {
        byte[] imageBytes = compressImageForAvatar(context, imageUri);
        if (imageBytes == null || imageBytes.length == 0) return null;
        RequestBody requestFile = RequestBody.create(MEDIA_TYPE_JPEG, imageBytes);
        return MultipartBody.Part.createFormData(partName, "avatar.jpg", requestFile);
    }
}
