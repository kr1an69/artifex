package com.artifex.app.api;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // 10.0.2.2 là IP cầu nối mặc định của Máy ảo Android trỏ thẳng vào localhost:8080 của máy tính
    // (Không bị chặn bởi Windows Firewall và không bị đổi khi chuyển Wi-Fi)
    private static final String BASE_URL = "http://10.0.2.2:8080/";
    // IP Wi-Fi dự phòng khi chạy trên điện thoại thật
    private static final String FALLBACK_LAN_HOST = "192.168.1.52";

    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            Interceptor fallbackInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request request = chain.request();
                    try {
                        return chain.proceed(request);
                    } catch (IOException e) {
                        // Nếu chạy trên máy thật không gọi được 10.0.2.2 thì tự động chuyển sang IP Wi-Fi
                        HttpUrl newUrl = request.url().newBuilder()
                                .host(FALLBACK_LAN_HOST)
                                .build();
                        Request fallbackRequest = request.newBuilder()
                                .url(newUrl)
                                .build();
                        return chain.proceed(fallbackRequest);
                    }
                }
            };

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .addInterceptor(fallbackInterceptor)
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static ApiService getApiService() {
        return getClient().create(ApiService.class);
    }
}
