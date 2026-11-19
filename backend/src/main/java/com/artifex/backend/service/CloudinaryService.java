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
     * Upload an image file to Cloudinary under the specified folder.
     * Returns the secure public URL of the uploaded image.
     * Tự động fallback sang unsignedUpload ("artifex_avatar") nếu backend chưa cấu hình Secret.
     */
    public String uploadImage(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image"
                    )
            );
            return (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            // Tự động fallback sang unsigned upload với preset "artifex_avatar"
            try {
                Map<?, ?> uploadResult = cloudinary.uploader().unsignedUpload(
                        file.getBytes(),
                        "artifex_avatar",
                        ObjectUtils.asMap(
                                "folder", folder,
                                "resource_type", "image"
                        )
                );
                return (String) uploadResult.get("secure_url");
            } catch (Exception fallbackEx) {
                throw new IOException("Lỗi upload ảnh lên Cloudinary: " + fallbackEx.getMessage(), fallbackEx);
            }
        }
    }
}
