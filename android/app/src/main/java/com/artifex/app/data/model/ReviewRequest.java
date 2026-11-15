package com.artifex.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class ReviewRequest implements Serializable {

    @SerializedName("productId")
    private Long productId;

    @SerializedName("userId")
    private Long userId;

    @SerializedName("rating")
    private Integer rating;

    @SerializedName("comment")
    private String comment;

    @SerializedName("imageUrl")
    private String imageUrl;

    public ReviewRequest() {
    }

    public ReviewRequest(Long productId, Long userId, Integer rating, String comment, String imageUrl) {
        this.productId = productId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
        this.imageUrl = imageUrl;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
