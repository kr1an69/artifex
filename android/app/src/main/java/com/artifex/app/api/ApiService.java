package com.artifex.app.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {

    // Endpoint kiểm tra kết nối tới Spring Boot backend
    @GET("api/health")
    Call<ResponseBody> checkHealth();

    // Các thành viên trong nhóm sẽ khai báo thêm các API methods của mình tại đây
}
