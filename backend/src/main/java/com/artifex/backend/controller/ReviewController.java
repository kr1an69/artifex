package com.artifex.backend.controller;

import com.artifex.backend.dto.ProductRatingSummaryDto;
import com.artifex.backend.dto.ReviewRequestDto;
import com.artifex.backend.dto.ReviewResponseDto;
import com.artifex.backend.entity.Product;
import com.artifex.backend.entity.Review;
import com.artifex.backend.entity.User;
import com.artifex.backend.repository.ProductRepository;
import com.artifex.backend.repository.ReviewRepository;
import com.artifex.backend.repository.UserRepository;
import com.artifex.backend.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller phụ trách các chức năng Review & Đánh giá sản phẩm (Bạn 3 - Đức)
 * Kỹ thuật trọng tâm:
 * - REST API GET/POST Review
 * - Tính rating trung bình & phân bố sao
 * - Upload ảnh Review lên Cloudinary
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    /**
     * Lấy danh sách đánh giá của 1 sản phẩm (Mới nhất lên đầu)
     * GET /api/reviews/product/{productId}
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ReviewResponseDto>> getReviewsByProduct(@PathVariable Long productId) {
        List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
        List<ReviewResponseDto> dtoList = reviews.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    /**
     * Lấy thống kê Rating trung bình và phân bố sao của 1 sản phẩm
     * GET /api/reviews/product/{productId}/summary
     */
    @GetMapping("/product/{productId}/summary")
    public ResponseEntity<ProductRatingSummaryDto> getProductRatingSummary(@PathVariable Long productId) {
        List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);

        int totalReviews = reviews.size();
        if (totalReviews == 0) {
            Map<Integer, Long> emptyStarCounts = new HashMap<>();
            for (int i = 1; i <= 5; i++) emptyStarCounts.put(i, 0L);
            return ResponseEntity.ok(ProductRatingSummaryDto.builder()
                    .productId(productId)
                    .averageRating(0.0)
                    .totalReviews(0)
                    .starCounts(emptyStarCounts)
                    .build());
        }

        double sumRating = reviews.stream().mapToInt(Review::getRating).sum();
        double avg = sumRating / totalReviews;
        double roundedAvg = BigDecimal.valueOf(avg)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();

        Map<Integer, Long> starCounts = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            final int star = i;
            long count = reviews.stream().filter(r -> r.getRating() != null && r.getRating() == star).count();
            starCounts.put(star, count);
        }

        ProductRatingSummaryDto summary = ProductRatingSummaryDto.builder()
                .productId(productId)
                .averageRating(roundedAvg)
                .totalReviews(totalReviews)
                .starCounts(starCounts)
                .build();

        return ResponseEntity.ok(summary);
    }

    /**
     * Tạo đánh giá mới (JSON Body)
     * POST /api/reviews
     */
    @PostMapping
    public ResponseEntity<?> createReview(@RequestBody ReviewRequestDto request) {
        if (request.getProductId() == null || request.getUserId() == null || request.getRating() == null) {
            return ResponseEntity.badRequest().body("productId, userId và rating không được để trống");
        }

        if (request.getRating() < 1 || request.getRating() > 5) {
            return ResponseEntity.badRequest().body("Rating phải từ 1 đến 5 sao");
        }

        Optional<Product> productOpt = productRepository.findById(request.getProductId());
        if (productOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy sản phẩm có id=" + request.getProductId());
        }

        Optional<User> userOpt = userRepository.findById(request.getUserId());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không tìm thấy người dùng có id=" + request.getUserId());
        }

        Review review = Review.builder()
                .product(productOpt.get())
                .user(userOpt.get())
                .rating(request.getRating())
                .comment(request.getComment() != null ? request.getComment() : "")
                .imageUrl(request.getImageUrl())
                .build();

        Review savedReview = reviewRepository.save(review);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponseDto(savedReview));
    }

    /**
     * API tiện ích: Tải file ảnh trực tiếp lên Cloudinary trả về URL
     * POST /api/reviews/upload-image
     */
    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadReviewImage(@RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = cloudinaryService.uploadImage(file);
            Map<String, String> response = new HashMap<>();
            response.put("imageUrl", imageUrl);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Upload ảnh lên Cloudinary thất bại: " + e.getMessage());
        }
    }

    /**
     * Helper mapper Review Entity -> ReviewResponseDto
     */
    private ReviewResponseDto mapToResponseDto(Review review) {
        return ReviewResponseDto.builder()
                .id(review.getId())
                .productId(review.getProduct() != null ? review.getProduct().getId() : null)
                .productName(review.getProduct() != null ? review.getProduct().getName() : null)
                .userId(review.getUser() != null ? review.getUser().getId() : null)
                .userName(review.getUser() != null ? review.getUser().getFullName() : "Người dùng")
                .userAvatar(review.getUser() != null ? review.getUser().getAvatarUrl() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .imageUrl(review.getImageUrl())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
