package com.artifex.backend.controller;

import com.artifex.backend.entity.Category;
import com.artifex.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final CategoryRepository categoryRepository;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Artifex PC Gear Store Backend (Spring Boot 4.1.1) is running smoothly!");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/test/categories")
    public ResponseEntity<List<Category>> testCategories() {
        // Endpoint mẫu test kết nối MySQL và trả về danh sách Category từ seed_data.sql
        List<Category> categories = categoryRepository.findAll();
        return ResponseEntity.ok(categories);
    }
}
