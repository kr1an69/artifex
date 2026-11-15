package com.artifex.app.ui.auth;

/**
 * Model độc lập trong package ui/auth phục vụ mục Gợi ý linh kiện trên trang Tài khoản,
 * tránh phụ thuộc chéo vào package ui/catalog của thành viên khác.
 */
public class RecommendedProduct {
    private final long id;
    private final String categoryName;
    private final String name;
    private final String description;
    private final long price;
    private final int stock;
    private final double ratingAvg;
    private final int reviewCount;
    private final String imageUrl;

    public RecommendedProduct(long id, String categoryName, String name, String description,
                              long price, int stock, double ratingAvg, int reviewCount, String imageUrl) {
        this.id = id;
        this.categoryName = categoryName != null ? categoryName : "";
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
        this.price = price;
        this.stock = stock;
        this.ratingAvg = ratingAvg;
        this.reviewCount = reviewCount;
        this.imageUrl = imageUrl != null ? imageUrl : "";
    }

    public long getId() {
        return id;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public double getRatingAvg() {
        return ratingAvg;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
