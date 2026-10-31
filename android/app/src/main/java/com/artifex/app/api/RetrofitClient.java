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

    // 127.0.0.1 (thông qua adb reverse) là đường truyền tối ưu:
    // - Kết nối trực tiếp máy chủ trong <1ms
    // - Miễn nhiễm hoàn toàn với việc đổi Wi-Fi (ở nhà, ở trường NLU-SV, quán cà phê)
    // - Không bị lỗi xung đột dải mạng 10.x.x.x của Wi-Fi trường đại học
    // - Vượt qua được tường lửa Windows Defender chặn kết nối vào
    private static final String BASE_URL = "http://127.0.0.1:8080/";

    // Danh sách Host chuẩn:
    // 1. "127.0.0.1" -> Chuẩn adb reverse (Máy ảo Android & máy thật cắm cáp USB)
    // 2. "10.0.2.2"  -> Chuẩn mặc định của Google Android Emulator khi không dùng reverse
    // 3. "10.0.3.2"  -> Cầu nối cho máy ảo Genymotion
    // 4. "localhost" -> Cổng loopback nội bộ
    private static final String[] CANDIDATE_HOSTS = new String[]{
            "127.0.0.1",
            "10.0.2.2",
            "10.0.3.2",
            "localhost"
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

                    // 2. Tự động thử lần lượt qua 127.0.0.1 -> 10.0.2.2 -> 10.0.3.2 -> localhost
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
                    .connectTimeout(2, TimeUnit.SECONDS)
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
