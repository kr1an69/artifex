package com.artifex.app.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

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

    // ==================== CATALOG & PRODUCT APIS (Đạt) ====================
    @retrofit2.http.GET("api/categories")
    retrofit2.Call<com.artifex.app.ui.catalog.model.CatalogApiResponse<java.util.List<com.artifex.app.ui.catalog.model.CategoryResponse>>> getCategories();

    @retrofit2.http.GET("api/products")
    retrofit2.Call<com.artifex.app.ui.catalog.model.CatalogApiResponse<com.artifex.app.ui.catalog.model.PageResponse<com.artifex.app.ui.catalog.model.ProductResponse>>> getProducts(
            @retrofit2.http.Query("page") Integer page,
            @retrofit2.http.Query("size") Integer size,
            @retrofit2.http.Query("sortBy") String sortBy,
            @retrofit2.http.Query("direction") String direction,
            @retrofit2.http.Query("categoryId") Long categoryId,
            @retrofit2.http.Query("keyword") String keyword
    );

    @retrofit2.http.GET("api/products/{id}")
    retrofit2.Call<com.artifex.app.ui.catalog.model.CatalogApiResponse<com.artifex.app.ui.catalog.model.ProductResponse>> getProductById(@retrofit2.http.Path("id") Long id);

    @retrofit2.http.POST("api/products")
    retrofit2.Call<com.artifex.app.ui.catalog.model.CatalogApiResponse<com.artifex.app.ui.catalog.model.ProductResponse>> createProduct(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.Multipart
    @retrofit2.http.POST("api/products/{id}/image")
    retrofit2.Call<com.artifex.app.ui.catalog.model.CatalogApiResponse<String>> uploadProductImage(
            @retrofit2.http.Path("id") Long id,
            @retrofit2.http.Part okhttp3.MultipartBody.Part file
    );
}
