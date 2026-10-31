package com.artifex.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		// Tự động giữ kết nối ADB reverse cho máy ảo / máy thật Android trên cổng 8080.
		// Giúp máy ảo luôn kết nối trực tiếp đến Spring Boot qua 127.0.0.1:8080:
		// 1. Không bị lỗi xung đột dải IP 10.x.x.x khi kết nối Wi-Fi trường đại học (NLU-SV).
		// 2. Không bị tường lửa Windows chặn cổng 8080 ở chế độ Public Network.
		// 3. Hoạt động trơn tru ở mọi nơi: ở trường, ở nhà, quán cafe hoặc offline.
		Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "artifex-adb-reverse-sync");
			t.setDaemon(true);
			return t;
		}).scheduleWithFixedDelay(() -> {
			try {
				String userHome = System.getProperty("user.home");
				File adbFile = new File(userHome, "AppData/Local/Android/Sdk/platform-tools/adb.exe");
				String adbCmd = adbFile.exists() ? adbFile.getAbsolutePath() : "adb";
				Process process = new ProcessBuilder(adbCmd, "reverse", "tcp:8080", "tcp:8080").start();
				process.waitFor(2, TimeUnit.SECONDS);
			} catch (Exception ignored) {
			}
		}, 0, 5, TimeUnit.SECONDS);
	}
}
