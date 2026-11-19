package com.artifex.app.ui.catalog.model;

import com.google.gson.annotations.SerializedName;

public class ProductResponse {

    @SerializedName("id")
    private Long id;

    @SerializedName("name")
    private String name;

    @SerializedName("price")
    private Double price;

    @SerializedName("stock")
    private Integer stock;

    @SerializedName("description")
    private String description;

    @SerializedName("imageUrl")
    private String imageUrl;

    @SerializedName("categoryId")
    private Long categoryId;

    @SerializedName("categoryName")
    private String categoryName;

    @SerializedName("ratingAvg")
    private Double ratingAvg;

    @SerializedName("reviewCount")
    private Integer reviewCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Double getRatingAvg() {
        return ratingAvg;
    }

    public void setRatingAvg(Double ratingAvg) {
        this.ratingAvg = ratingAvg;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }
}
