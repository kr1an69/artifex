package com.artifex.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryCreateRequest {
    @NotBlank(message = "Category name is required")
    @Size(max = 50, message = "Name must be less than 50 characters")
    private String name;

    private String description;
    
    private String imageUrl;
}
