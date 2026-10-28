package com.artifex.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRatingSummaryDto {
    private Long productId;
    private Double averageRating; // Ví dụ: 4.5
    private Integer totalReviews; // Tổng số lượt review
    private Map<Integer, Long> starCounts; // Số lượng theo từng mức sao: {5: 10, 4: 2, 3: 0, 2: 0, 1: 0}
}
