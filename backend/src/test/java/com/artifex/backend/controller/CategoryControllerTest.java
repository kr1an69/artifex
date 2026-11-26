package com.artifex.backend.controller;

import com.artifex.backend.dto.request.CategoryCreateRequest;
import com.artifex.backend.dto.response.ApiResponse;
import com.artifex.backend.dto.response.CategoryResponse;
import com.artifex.backend.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho CategoryController (Catalog - Phụ trách: Đạt).
 */
class CategoryControllerTest {

    private CategoryService categoryService;
    private CategoryController categoryController;

    @BeforeEach
    void setup() {
        categoryService = mock(CategoryService.class);
        categoryController = new CategoryController(categoryService);
    }

    @Test
    void getAllCategoriesReturnsSuccessList() {
        List<CategoryResponse> categories = List.of(
                CategoryResponse.builder().id(1L).name("CPU").description("Vi xử lý").build(),
                CategoryResponse.builder().id(2L).name("GPU").description("Card đồ họa").build()
        );
        when(categoryService.getAllCategories()).thenReturn(categories);

        ResponseEntity<ApiResponse<List<CategoryResponse>>> response = categoryController.getAllCategories();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getStatusCode());
        assertEquals(2, response.getBody().getData().size());
        verify(categoryService).getAllCategories();
    }

    @Test
    void getCategoryByIdReturnsCategoryWhenFound() {
        CategoryResponse cpu = CategoryResponse.builder().id(1L).name("CPU").build();
        when(categoryService.getCategoryById(1L)).thenReturn(cpu);

        ResponseEntity<ApiResponse<CategoryResponse>> response = categoryController.getCategoryById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CPU", response.getBody().getData().getName());
    }

    @Test
    void getCategoryByIdReturnsNotFoundWhenMissing() {
        when(categoryService.getCategoryById(999L))
                .thenThrow(new RuntimeException("Category not found with id: 999"));

        ResponseEntity<ApiResponse<CategoryResponse>> response = categoryController.getCategoryById(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatusCode());
    }

    @Test
    void createCategoryReturnsCreatedResponse() {
        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .name("Monitor")
                .description("Màn hình Gaming")
                .build();
        CategoryResponse created = CategoryResponse.builder()
                .id(8L)
                .name("Monitor")
                .description("Màn hình Gaming")
                .build();
        when(categoryService.createCategory(any(CategoryCreateRequest.class))).thenReturn(created);

        ResponseEntity<ApiResponse<CategoryResponse>> response = categoryController.createCategory(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(201, response.getBody().getStatusCode());
        assertEquals("Monitor", response.getBody().getData().getName());
    }

    @Test
    void createCategoryReturnsBadRequestWhenDuplicate() {
        CategoryCreateRequest request = CategoryCreateRequest.builder().name("CPU").build();
        when(categoryService.createCategory(any(CategoryCreateRequest.class)))
                .thenThrow(new RuntimeException("Category with name CPU already exists"));

        ResponseEntity<ApiResponse<CategoryResponse>> response = categoryController.createCategory(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatusCode());
    }
}
