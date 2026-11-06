package com.artifex.backend.controller;

import com.artifex.backend.dto.request.ProductCreateRequest;
import com.artifex.backend.dto.response.ApiResponse;
import com.artifex.backend.dto.response.ProductResponse;
import com.artifex.backend.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword
    ) {
        try {
            Page<ProductResponse> products = productService.getProducts(page, size, sortBy, direction, categoryId, keyword);
            return ResponseEntity.ok(ApiResponse.success(products, "Fetched products successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Error fetching products: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        try {
            ProductResponse product = productService.getProductById(id);
            return ResponseEntity.ok(ApiResponse.success(product, "Fetched product successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductCreateRequest request) {
        try {
            ProductResponse createdProduct = productService.createProduct(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.created(createdProduct, "Product created successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<ApiResponse<String>> uploadProductImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            String imageUrl = productService.uploadImage(id, file);
            return ResponseEntity.ok(ApiResponse.<String>builder()
                    .statusCode(200)
                    .message("Tải ảnh lên thành công")
                    .data(imageUrl)
                    .build());
        } catch (IllegalArgumentException e) {
            // Trả về 400 Bad Request nếu file rỗng / không hợp lệ
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<String>builder()
                            .statusCode(400)
                            .message(e.getMessage())
                            .build());
        } catch (RuntimeException e) {
            // Trả về 404 Not Found nếu không tìm thấy Product ID
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<String>builder()
                            .statusCode(404)
                            .message(e.getMessage())
                            .build());
        }
    }
}
