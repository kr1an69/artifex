package com.artifex.backend.controller;

import com.artifex.backend.dto.request.ProductCreateRequest;
import com.artifex.backend.dto.response.ApiResponse;
import com.artifex.backend.dto.response.ProductResponse;
import com.artifex.backend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho ProductController (Catalog & Cloudinary Image - Phụ trách: Đạt).
 */
class ProductControllerTest {

    private ProductService productService;
    private ProductController productController;

    @BeforeEach
    void setup() {
        productService = mock(ProductService.class);
        productController = new ProductController(productService);
    }

    @Test
    void getProductsReturnsPagedSuccessResponse() {
        ProductResponse cpu = ProductResponse.builder()
                .id(1L)
                .name("Intel Core i5-13600K")
                .price(new BigDecimal("7890000"))
                .stock(25)
                .categoryId(1L)
                .categoryName("CPU")
                .build();
        Page<ProductResponse> page = new PageImpl<>(List.of(cpu));
        when(productService.getProducts(0, 10, "price", "ASC", 1L, "i5")).thenReturn(page);

        ResponseEntity<ApiResponse<Page<ProductResponse>>> response =
                productController.getProducts(0, 10, "price", "ASC", 1L, "i5");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
        assertEquals("Intel Core i5-13600K", response.getBody().getData().getContent().get(0).getName());
        verify(productService).getProducts(0, 10, "price", "ASC", 1L, "i5");
    }

    @Test
    void getProductByIdReturnsProductWhenFound() {
        ProductResponse gpu = ProductResponse.builder()
                .id(4L)
                .name("NVIDIA GeForce RTX 4070 Super 12GB")
                .price(new BigDecimal("16990000"))
                .build();
        when(productService.getProductById(4L)).thenReturn(gpu);

        ResponseEntity<ApiResponse<ProductResponse>> response = productController.getProductById(4L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4L, response.getBody().getData().getId());
    }

    @Test
    void getProductByIdReturnsNotFoundWhenMissing() {
        when(productService.getProductById(999L))
                .thenThrow(new RuntimeException("Product not found with id: 999"));

        ResponseEntity<ApiResponse<ProductResponse>> response = productController.getProductById(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatusCode());
    }

    @Test
    void createProductReturnsCreatedResponse() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .name("RTX 4080 Super")
                .price(new BigDecimal("31990000"))
                .stock(10)
                .categoryId(2L)
                .description("16GB GDDR6X")
                .build();
        ProductResponse created = ProductResponse.builder()
                .id(20L)
                .name("RTX 4080 Super")
                .price(new BigDecimal("31990000"))
                .stock(10)
                .categoryId(2L)
                .categoryName("GPU")
                .build();
        when(productService.createProduct(any(ProductCreateRequest.class))).thenReturn(created);

        ResponseEntity<ApiResponse<ProductResponse>> response = productController.createProduct(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(201, response.getBody().getStatusCode());
        assertEquals(20L, response.getBody().getData().getId());
    }

    @Test
    void createProductReturnsBadRequestWhenCategoryMissing() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .name("Unknown GPU")
                .price(new BigDecimal("1000000"))
                .stock(5)
                .categoryId(999L)
                .build();
        when(productService.createProduct(any(ProductCreateRequest.class)))
                .thenThrow(new RuntimeException("Category not found with id: 999"));

        ResponseEntity<ApiResponse<ProductResponse>> response = productController.createProduct(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatusCode());
    }

    @Test
    void uploadProductImageReturnsCloudinaryUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "gpu.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        String expectedUrl = "https://res.cloudinary.com/demo/image/upload/artifex/products/gpu.jpg";
        when(productService.uploadProductImage(eq(4L), any())).thenReturn(expectedUrl);

        ResponseEntity<ApiResponse<String>> response = productController.uploadProductImage(4L, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getStatusCode());
        assertEquals(expectedUrl, response.getBody().getData());
    }

    @Test
    void uploadProductImageReturnsBadRequestWhenFileInvalid() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);
        when(productService.uploadProductImage(eq(4L), any()))
                .thenThrow(new IllegalArgumentException("File cannot be empty"));

        ResponseEntity<ApiResponse<String>> response = productController.uploadProductImage(4L, emptyFile);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatusCode());
    }

    @Test
    void uploadProductImageReturnsInternalServerErrorWhenIoFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "gpu.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        when(productService.uploadProductImage(eq(4L), any()))
                .thenThrow(new IOException("Network timeout"));

        ResponseEntity<ApiResponse<String>> response = productController.uploadProductImage(4L, file);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatusCode());
    }

    @Test
    void uploadProductImageReturnsNotFoundWhenProductMissing() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "gpu.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );
        when(productService.uploadProductImage(eq(999L), any()))
                .thenThrow(new RuntimeException("Product not found with id: 999"));

        ResponseEntity<ApiResponse<String>> response = productController.uploadProductImage(999L, file);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatusCode());
    }
}
