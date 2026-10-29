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
    private static final String BASE_URL = "http://10.0.2.2:8080/";

    // Danh sách IP tự động dò tìm theo thứ tự ưu tiên:
    // 1. 10.0.2.2 (Cầu nối chuẩn của Máy ảo Android)
    // 2. 192.168.1.73 (IPv4 Wi-Fi hiện tại của Laptop)
    // 3. 127.0.0.1 (Cầu nối adb reverse)
    // 4. 192.168.1.52 (IPv4 Wi-Fi dự phòng cũ)
    private static final String[] CANDIDATE_HOSTS = new String[]{
            "10.0.2.2",
            "192.168.1.73",
            "127.0.0.1",
            "192.168.1.52"
    };

    private static volatile String lastWorkingHost = null;
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            Interceptor fallbackInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request originalRequest = chain.request();
                    IOException firstException = null;

                    // 1. Nếu đã tìm thấy host hoạt động ở lần gọi trước -> gọi thẳng vào host đó ngay lập tức
                    String cachedHost = lastWorkingHost;
                    if (cachedHost != null) {
                        try {
                            HttpUrl cachedUrl = originalRequest.url().newBuilder()
                                    .host(cachedHost)
                                    .build();
                            return chain.proceed(originalRequest.newBuilder().url(cachedUrl).build());
                        } catch (IOException e) {
                            firstException = e;
                            lastWorkingHost = null;
                        }
                    }

                    // 2. Tự động thử lần lượt qua 10.0.2.2 -> 192.168.1.73 -> 127.0.0.1
                    for (String host : CANDIDATE_HOSTS) {
                        if (host.equals(cachedHost)) continue;
                        try {
                            HttpUrl newUrl = originalRequest.url().newBuilder()
                                    .host(host)
                                    .build();
                            Request candidateRequest = originalRequest.newBuilder()
                                    .url(newUrl)
                                    .build();
                            Response response = chain.proceed(candidateRequest);
                            lastWorkingHost = host;
                            return response;
                        } catch (IOException e) {
                            if (firstException == null) {
                                firstException = e;
                            }
                        }
                    }

                    throw firstException != null ? firstException : new IOException("Không thể kết nối tới máy chủ Spring Boot (cổng 8080)");
                }
            };

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
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
