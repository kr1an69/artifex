package com.artifex.app.api;

import com.artifex.app.data.model.ProductRatingSummary;
import com.artifex.app.data.model.ReviewRequest;
import com.artifex.app.data.model.ReviewResponse;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import java.util.List;
import java.util.Map;

public interface ApiService {

    @GET("api/health")
    Call<ResponseBody> checkHealth();

    // ==================== AUTH & USER APIS (Khoa) ====================
    @retrofit2.http.POST("api/auth/register/send-otp")
    retrofit2.Call<java.util.Map<String, Object>> sendRegisterOtp(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.POST("api/auth/register")
    retrofit2.Call<java.util.Map<String, Object>> register(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.POST("api/auth/login")
    retrofit2.Call<java.util.Map<String, Object>> login(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.POST("api/auth/firebase")
    retrofit2.Call<java.util.Map<String, Object>> firebaseLogin(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.POST("api/auth/logout")
    retrofit2.Call<java.util.Map<String, Object>> logout();

    @retrofit2.http.POST("api/auth/logout")
    retrofit2.Call<java.util.Map<String, Object>> logout(@retrofit2.http.Header("Authorization") String authHeader);

    @retrofit2.http.POST("api/auth/forgot-password/send-otp")
    retrofit2.Call<java.util.Map<String, Object>> requestForgotPasswordOtp(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.POST("api/auth/forgot-password")
    retrofit2.Call<java.util.Map<String, Object>> forgotPassword(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.GET("api/users/{id}")
    retrofit2.Call<java.util.Map<String, Object>> getUserProfile(@retrofit2.http.Path("id") Long id);

    @retrofit2.http.PUT("api/users/{id}")
    retrofit2.Call<java.util.Map<String, Object>> updateProfile(@retrofit2.http.Path("id") Long id, @retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.PUT("api/users/{id}/avatar")
    retrofit2.Call<java.util.Map<String, Object>> updateAvatar(@retrofit2.http.Path("id") Long id, @retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.PUT("api/users/{id}/password")
    retrofit2.Call<java.util.Map<String, Object>> changePassword(@retrofit2.http.Path("id") Long id, @retrofit2.http.Body java.util.Map<String, Object> body);

    // ==================== STORE & ORDER HISTORY APIS (Bạn 5) ====================
    @retrofit2.http.GET("api/orders/user/{userId}")
    retrofit2.Call<java.util.List<com.artifex.app.ui.store.OrderModel>> getOrderHistoryByUser(@retrofit2.http.Path("userId") Long userId);

    @retrofit2.http.PUT("api/orders/{orderId}/cancel")
    retrofit2.Call<java.util.Map<String, Object>> cancelOrder(@retrofit2.http.Path("orderId") Long orderId);

    // ==================== PRODUCTS & RECOMMENDATION APIS (Đạt) ====================
    @retrofit2.http.GET("api/products")
    retrofit2.Call<java.util.Map<String, Object>> getProducts(@retrofit2.http.Query("size") int size);

    @retrofit2.http.GET("api/products/{id}")
    retrofit2.Call<java.util.Map<String, Object>> getProductById(@retrofit2.http.Path("id") Long id);

    // ==================== REVIEW & RATING APIS (Đức) ====================
    @GET("api/reviews/product/{productId}")
    Call<List<ReviewResponse>> getReviewsByProduct(@Path("productId") Long productId);

    @GET("api/reviews/product/{productId}/summary")
    Call<ProductRatingSummary> getProductRatingSummary(@Path("productId") Long productId);

    @POST("api/reviews")
    Call<ReviewResponse> createReview(@Body ReviewRequest request);

    @Multipart
    @POST("api/reviews/upload-image")
    Call<Map<String, String>> uploadReviewImage(@Part MultipartBody.Part file);
}
