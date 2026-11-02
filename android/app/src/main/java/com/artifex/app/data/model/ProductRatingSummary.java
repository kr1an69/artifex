package com.artifex.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Map;

public class ProductRatingSummary implements Serializable {

    @SerializedName("productId")
    private Long productId;

    @SerializedName("averageRating")
    private Double averageRating;

    @SerializedName("totalReviews")
    private Integer totalReviews;

    @SerializedName("starCounts")
    private Map<Integer, Long> starCounts;

    public ProductRatingSummary() {
    }

    public ProductRatingSummary(Long productId, Double averageRating, Integer totalReviews, Map<Integer, Long> starCounts) {
        this.productId = productId;
        this.averageRating = averageRating;
        this.totalReviews = totalReviews;
        this.starCounts = starCounts;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }

    public Map<Integer, Long> getStarCounts() {
        return starCounts;
    }

    public void setStarCounts(Map<Integer, Long> starCounts) {
        this.starCounts = starCounts;
    }
}
