package com.artifex.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Body của API POST /api/orders (Checkout).
 * Giá tiền KHÔNG nhận từ client, server tự lấy từ bảng products để tránh bị sửa giá.
 */
@Data
public class OrderCreateRequest {

    @NotNull(message = "userId không được để trống")
    private Long userId;

    @NotBlank(message = "Tên người nhận không được để trống")
    private String receiverName;

    @NotBlank(message = "Số điện thoại người nhận không được để trống")
    private String receiverPhone;

    @NotBlank(message = "Địa chỉ giao hàng không được để trống")
    private String shippingAddress;

    @Valid
    @NotEmpty(message = "Giỏ hàng đang trống")
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull(message = "productId không được để trống")
        private Long productId;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải >= 1")
        private Integer quantity;
    }
}
