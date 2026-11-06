package com.artifex.app.ui.store;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Model đại diện cho 1 Đơn hàng nhận về từ API GET /api/orders/user/{userId}
 * Khớp 100% với cấu trúc JSON Map trả về từ OrderHistoryController ở Backend.
 */
public class OrderModel implements Serializable {

    private Long id;
    private Long userId;
    private double totalPrice;
    private String status; // PENDING, SHIPPING, COMPLETED, CANCELLED
    private String receiverName;
    private String receiverPhone;
    private String shippingAddress;
    private String createdAt;
    private List<OrderItemModel> items;

    public OrderModel() {
        this.items = new ArrayList<>();
    }

    public OrderModel(Long id, Long userId, double totalPrice, String status,
                      String receiverName, String receiverPhone, String shippingAddress,
                      String createdAt, List<OrderItemModel> items) {
        this.id = id;
        this.userId = userId;
        this.totalPrice = totalPrice;
        this.status = status;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.shippingAddress = shippingAddress;
        this.createdAt = createdAt;
        this.items = items != null ? items : new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getStatus() {
        return status != null ? status : "PENDING";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReceiverName() {
        return receiverName != null ? receiverName : "Khách hàng";
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone != null ? receiverPhone : "";
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getShippingAddress() {
        return shippingAddress != null ? shippingAddress : "";
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getCreatedAt() {
        return createdAt != null ? createdAt : "";
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItemModel> getItems() {
        return items != null ? items : new ArrayList<>();
    }

    public void setItems(List<OrderItemModel> items) {
        this.items = items;
    }

    /**
     * Tính tổng số lượng sản phẩm (cộng dồn quantity của từng món trong đơn)
     */
    public int getTotalItemCount() {
        if (items == null) return 0;
        int sum = 0;
        for (OrderItemModel item : items) {
            sum += item.getQuantity();
        }
        return sum;
    }

    /**
     * Model đại diện cho từng dòng chi tiết linh kiện ("items") trong đơn hàng
     */
    public static class OrderItemModel implements Serializable {
        private Long id;
        private ProductInfo product;
        private int quantity;
        private double unitPrice;

        public OrderItemModel() {
        }

        public OrderItemModel(Long id, ProductInfo product, int quantity, double unitPrice) {
            this.id = id;
            this.product = product;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public ProductInfo getProduct() {
            return product;
        }

        public void setProduct(ProductInfo product) {
            this.product = product;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public double getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(double unitPrice) {
            this.unitPrice = unitPrice;
        }
    }

    /**
     * Model thông tin tóm tắt của Product bên trong OrderItemModel
     */
    public static class ProductInfo implements Serializable {
        private Long id;
        private String name;
        private double price;
        private String imageUrl;
        private String description;

        public ProductInfo() {
        }

        public ProductInfo(Long id, String name, double price, String imageUrl, String description) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.imageUrl = imageUrl;
            this.description = description;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name != null ? name : "Linh kiện Artifex";
        }

        public void setName(String name) {
            this.name = name;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
