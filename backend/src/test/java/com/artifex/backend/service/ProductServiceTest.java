package com.artifex.backend.service;

import com.artifex.backend.dto.request.ProductCreateRequest;
import com.artifex.backend.dto.response.ProductResponse;
import com.artifex.backend.entity.Category;
import com.artifex.backend.entity.Product;
import com.artifex.backend.repository.CategoryRepository;
import com.artifex.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho ProductService (Catalog & Cloudinary Upload - Phụ trách: Đạt).
 */
class ProductServiceTest {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private CloudinaryService cloudinaryService;
    private ProductService productService;

    @BeforeEach
    void setup() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        cloudinaryService = mock(CloudinaryService.class);
        productService = new ProductService(productRepository, categoryRepository, cloudinaryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getProductsMapsPageToResponse() {
        Category cpuCat = Category.builder().id(1L).name("CPU").build();
        Product product = Product.builder()
                .id(1L)
                .name("Intel Core i5-13600K")
                .price(new BigDecimal("7890000"))
                .stock(25)
                .imageUrl("https://img.example/cpu.jpg")
                .category(cpuCat)
                .ratingAvg(5.0)
                .reviewCount(2)
                .build();

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        Page<ProductResponse> result = productService.getProducts(0, 10, "price", "ASC", 1L, "i5");

        assertEquals(1, result.getTotalElements());
        assertEquals("CPU", result.getContent().get(0).getCategoryName());
    }

    @Test
    void createProductPersistsWithCategoryAndDefaultRatings() {
        Category gpuCat = Category.builder().id(2L).name("GPU").build();
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(gpuCat));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });

        ProductCreateRequest req = ProductCreateRequest.builder()
                .name("RTX 4070 Ti")
                .price(new BigDecimal("21990000"))
                .stock(12)
                .description("12GB GDDR6X")
                .categoryId(2L)
                .build();

        ProductResponse response = productService.createProduct(req);

        assertEquals(50L, response.getId());
        assertEquals("", response.getImageUrl());
        assertEquals(0.0, response.getRatingAvg());
        assertEquals(0, response.getReviewCount());
        assertEquals("GPU", response.getCategoryName());
    }

    @Test
    void uploadProductImageUploadsToCloudinaryProductsFolderAndSavesUrl() throws Exception {
        Category cat = Category.builder().id(2L).name("GPU").build();
        Product existing = Product.builder()
                .id(4L)
                .name("RTX 4070 Super")
                .price(new BigDecimal("16990000"))
                .stock(10)
                .imageUrl("")
                .category(cat)
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "file", "rtx.jpg", "image/jpeg", new byte[]{10, 20, 30}
        );
        String uploadedUrl = "https://res.cloudinary.com/wpheip7x/image/upload/artifex/products/rtx.jpg";

        when(productRepository.findById(4L)).thenReturn(Optional.of(existing));
        when(cloudinaryService.uploadImage(eq(file), eq("artifex/products"))).thenReturn(uploadedUrl);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        String resultUrl = productService.uploadProductImage(4L, file);

        assertEquals(uploadedUrl, resultUrl);
        assertEquals(uploadedUrl, existing.getImageUrl());
        verify(cloudinaryService).uploadImage(file, "artifex/products");
        verify(productRepository).save(existing);
    }

    @Test
    void getProductByIdThrowsWhenMissing() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> productService.getProductById(404L));
    }
}
