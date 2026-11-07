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
}
