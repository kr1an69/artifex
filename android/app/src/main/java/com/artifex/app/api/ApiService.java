package com.artifex.app.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {

    // Endpoint kiểm tra kết nối tới Spring Boot backend
    @GET("api/health")
    Call<ResponseBody> checkHealth();

    // ==================== AUTH & USER APIS (Khoa) ====================
    @retrofit2.http.POST("api/auth/register")
    retrofit2.Call<java.util.Map<String, Object>> register(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.POST("api/auth/login")
    retrofit2.Call<java.util.Map<String, Object>> login(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.POST("api/auth/firebase")
    retrofit2.Call<java.util.Map<String, Object>> firebaseLogin(@retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.GET("api/users/{id}")
    retrofit2.Call<java.util.Map<String, Object>> getUserProfile(@retrofit2.http.Path("id") Long id);

    @retrofit2.http.PUT("api/users/{id}")
    retrofit2.Call<java.util.Map<String, Object>> updateProfile(@retrofit2.http.Path("id") Long id, @retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.PUT("api/users/{id}/avatar")
    retrofit2.Call<java.util.Map<String, Object>> updateAvatar(@retrofit2.http.Path("id") Long id, @retrofit2.http.Body java.util.Map<String, Object> body);

    @retrofit2.http.PUT("api/users/{id}/password")
    retrofit2.Call<java.util.Map<String, Object>> changePassword(@retrofit2.http.Path("id") Long id, @retrofit2.http.Body java.util.Map<String, Object> body);
}
