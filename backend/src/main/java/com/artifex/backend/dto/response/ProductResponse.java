package com.artifex.backend.dto.response;

import com.artifex.backend.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String description;
    private String imageUrl;
    private Long categoryId;
    private String categoryName;
    private Double ratingAvg;
    private Integer reviewCount;
    private LocalDateTime createdAt;

    public static ProductResponse fromEntity(Product product) {
        if (product == null) return null;
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .ratingAvg(product.getRatingAvg())
                .reviewCount(product.getReviewCount())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
