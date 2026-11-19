package com.artifex.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Upload an image file to Cloudinary under the "artifex/reviews" folder.
     * Returns the secure public URL of the uploaded image.
     */
    public String uploadImage(MultipartFile file) throws IOException {
        return uploadImage(file, "artifex/reviews");
    }

    /**
     * Upload an image file to Cloudinary under the specified folder using signed upload,
     * falling back to unsigned upload ("artifex_avatar") when API key/secret are not configured locally.
     * Returns the secure public URL of the uploaded image.
     */
    public String uploadImage(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        byte[] fileBytes = file.getBytes();
        if (hasSignedCredentials()) {
            try {
                Map<?, ?> uploadResult = cloudinary.uploader().upload(
                        fileBytes,
                        ObjectUtils.asMap(
                                "folder", folder,
                                "resource_type", "image"
                        )
                );
                return (String) uploadResult.get("secure_url");
            } catch (Exception signedEx) {
                System.err.println("[Cloudinary Signed Upload Fallback] " + signedEx.getMessage());
            }
        }

        try {
            Map<?, ?> unsignedResult = cloudinary.uploader().unsignedUpload(
                    fileBytes,
                    "artifex_avatar",
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image"
                    )
            );
            return (String) unsignedResult.get("secure_url");
        } catch (Exception e) {
            throw new IOException("Lỗi upload ảnh lên Cloudinary: " + e.getMessage(), e);
        }
    }

    private boolean hasSignedCredentials() {
        if (cloudinary == null || cloudinary.config == null) {
            return false;
        }
        String key = cloudinary.config.apiKey;
        String secret = cloudinary.config.apiSecret;
        return key != null && !key.trim().isEmpty() && !"demo".equalsIgnoreCase(key.trim())
                && secret != null && !secret.trim().isEmpty() && !"demo".equalsIgnoreCase(secret.trim());
    }
}
