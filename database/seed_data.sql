-- ========================================================
-- ARTIFEX PC GEAR STORE - DATABASE INITIALIZATION & SEED DATA
-- Database: artifex_db
-- Target DBMS: MySQL 8.0+
-- ========================================================

CREATE DATABASE IF NOT EXISTS artifex_db 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE artifex_db;

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS order_details;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS stores;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- 1. BẢNG USERS
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) DEFAULT NULL,
    address VARCHAR(255) DEFAULT NULL,
    avatar_url VARCHAR(500) DEFAULT 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80',
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. BẢNG CATEGORIES
CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255) DEFAULT NULL,
    image_url VARCHAR(500) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. BẢNG PRODUCTS
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 10,
    description TEXT DEFAULT NULL,
    image_url VARCHAR(500) NOT NULL,
    category_id BIGINT NOT NULL,
    rating_avg DOUBLE DEFAULT 5.0,
    review_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. BẢNG REVIEWS
CREATE TABLE reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT NOT NULL,
    image_url VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. BẢNG STORES
CREATE TABLE stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    phone VARCHAR(20) NOT NULL,
    opening_hours VARCHAR(50) DEFAULT '08:00 - 21:30'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. BẢNG ORDERS
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_price DECIMAL(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    receiver_name VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. BẢNG ORDER_DETAILS
CREATE TABLE order_details (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_detail_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_detail_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- SEED USERS
INSERT INTO users (id, username, email, password, full_name, phone, address, avatar_url, role) VALUES
(1, 'nguyenvana', 'vana@artifex.vn', '123456', 'Nguyễn Văn A', '0901234567', 'Khu phố 6, P. Linh Trung, TP. Thủ Đức, TP.HCM', 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=300&q=80', 'USER'),
(2, 'admin_artifex', 'admin@artifex.vn', '123456', 'Trần Quản Trị', '0988776655', '398 Nguyễn Tri Phương, P. 4, Q. 10, TP.HCM', 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?auto=format&fit=crop&w=300&q=80', 'ADMIN');

-- SEED CATEGORIES
INSERT INTO categories (id, name, description, image_url) VALUES
(1, 'CPU', 'Bộ vi xử lý trung tâm Intel & AMD Ryzen', 'https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=600&q=80'),
(2, 'GPU', 'Card màn hình đồ họa NVIDIA GeForce & AMD Radeon', 'https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=600&q=80'),
(3, 'RAM', 'Bộ nhớ trong DDR4, DDR5 tốc độ cao', 'https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=600&q=80'),
(4, 'SSD', 'Ổ cứng thể rắn M.2 NVMe PCIe 4.0 & SATA 3', 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=600&q=80'),
(5, 'Mainboard', 'Bo mạch chủ socket LGA1700, AM5 đa tính năng', 'https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=600&q=80'),
(6, 'Case', 'Vỏ case máy tính tản nhiệt tốt, mặt kính cường lực', 'https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=600&q=80'),
(7, 'PSU', 'Nguồn máy tính chuẩn 80 Plus Bronze / Gold', 'https://images.unsplash.com/photo-1600003014755-ba31aa59c4b6?auto=format&fit=crop&w=600&q=80');

-- SEED PRODUCTS
INSERT INTO products (id, name, price, stock, description, image_url, category_id, rating_avg, review_count) VALUES
(1, 'Intel Core i5-13600K', 7890000.00, 25, '14 nhân (6 P-Core + 8 E-Core), 20 luồng, xung nhịp turbo lên tới 5.1 GHz. Tối ưu hoàn hảo cho cả Gaming và Render đồ họa nặng.', 'https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=800&q=80', 1, 5.0, 2),
(2, 'AMD Ryzen 7 7800X3D', 10490000.00, 15, 'CPU chơi game mạnh mẽ nhất thế giới với công nghệ 3D V-Cache đột phá, 8 nhân 16 luồng socket AM5 mát mẻ và tiết kiệm điện.', 'https://images.unsplash.com/photo-1555680202-c86f0e12f086?auto=format&fit=crop&w=800&q=80', 1, 4.8, 1),
(3, 'Intel Core i7-14700K', 11290000.00, 12, '20 nhân 28 luồng, xung nhịp khủng 5.6 GHz, lựa chọn hàng đầu cho streamer và nhà sáng tạo nội dung chuyên nghiệp.', 'https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=800&q=80', 1, 5.0, 0),
(4, 'NVIDIA GeForce RTX 4070 Super 12GB', 16990000.00, 10, 'Kiến trúc Ada Lovelace, hỗ trợ DLSS 3 Frame Generation và Ray Tracing thế hệ mới. Chiến mượt mà mọi tựa game AAA ở độ phân giải 2K.', 'https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=800&q=80', 2, 5.0, 1),
(5, 'ASUS Dual GeForce RTX 4060 Ti 8GB OC', 10590000.00, 20, 'Thiết kế 2 quạt tản nhiệt Axial-tech êm ái, tối ưu cho phân khúc màn hình Full HD & 2K, tiết kiệm điện năng vượt trội.', 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?auto=format&fit=crop&w=800&q=80', 2, 4.5, 0),
(6, 'PowerColor Radeon RX 7800 XT 16GB', 14200000.00, 8, 'Dung lượng VRAM khủng 16GB GDDR6, băng thông lớn, hiệu năng thuần cực mạnh mẽ cho các tác vụ đồ họa và game góc nhìn thứ nhất.', 'https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=800&q=80', 2, 4.7, 0),
(7, 'Corsair Vengeance RGB DDR5 32GB (2x16GB) 6000MHz', 3190000.00, 30, 'Kit RAM DDR5 hiệu năng cao với dải LED RGB 10 vùng siêu sáng, hỗ trợ chuẩn ép xung Intel XMP 3.0 và AMD EXPO.', 'https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80', 3, 5.0, 0),
(8, 'Kingston Fury Beast RGB 16GB (2x8GB) DDR4 3200MHz', 1190000.00, 40, 'Thiết kế tản nhiệt kim loại hầm hố, LED RGB đồng bộ linh hoạt, tính tương thích cực cao với mọi bo mạch chủ phổ thông.', 'https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80', 3, 4.9, 0),
(9, 'Samsung 990 Pro 1TB NVMe M.2 PCIe Gen 4.0', 2890000.00, 25, 'Tốc độ đọc/ghi tuần tự đỉnh cao lên tới 7,450 / 6,900 MB/s. Độ bền bỉ hàng đầu phục vụ load game tức thì và làm việc nặng.', 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=800&q=80', 4, 5.0, 0),
(10, 'Kingston NV2 1TB PCIe 4.0 NVMe M.2', 1490000.00, 35, 'Giải pháp nâng cấp dung lượng lưu trữ siêu tiết kiệm, tốc độ đọc 3,500MB/s đáp ứng hoàn hảo nhu cầu học tập và giải trí.', 'https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=800&q=80', 4, 4.6, 0),
(11, 'ASUS ROG STRIX B760-F GAMING WIFI', 5490000.00, 15, 'Tụ điện cao cấp 16+1 phase nguồn, hỗ trợ PCIe 5.0, Wi-Fi 6E và thiết kế ROG Gaming hầm hố đậm chất game thủ.', 'https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80', 5, 5.0, 0),
(12, 'MSI MAG B650 TOMAHAWK WIFI', 5190000.00, 14, 'Nền tảng AM5 bền bỉ, tản nhiệt VRM Extended Heatsink dầy dặn, tối ưu cho dòng vi xử lý AMD Ryzen 7000 series.', 'https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80', 5, 4.8, 0),
(13, 'NZXT H5 Flow RGB Black', 2490000.00, 18, 'Vỏ case Mid-Tower mặt lưới thoáng khí tối đa, kèm sẵn 2 quạt F140 RGB phía trước và quạt thổi chuyên dụng cho GPU.', 'https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=800&q=80', 6, 5.0, 0),
(14, 'Corsair RM850e 850W 80 Plus Gold ATX 3.0', 3190000.00, 20, 'Nguồn Full Modular nhỏ gọn, tụ chuẩn công nghiệp, chứng nhận 80 PLUS Gold và cổng PCIe 5.0 12VHPWR sẵn sàng cho RTX 40 series.', 'https://images.unsplash.com/photo-1600003014755-ba31aa59c4b6?auto=format&fit=crop&w=800&q=80', 7, 5.0, 0);

-- SEED REVIEWS
INSERT INTO reviews (id, product_id, user_id, rating, comment, image_url) VALUES
(1, 1, 1, 5, 'Chip i5-13600K chạy rất khỏe, render video 4K Premiere mượt mà không bị giật lag!', 'https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=600&q=80'),
(2, 1, 2, 5, 'Nhiệt độ tương đối ổn định khi đi cùng tản khí Deepcool AK620. Đóng gói giao nhanh!', NULL),
(3, 4, 1, 5, 'RTX 4070 Super chiến Black Myth Wukong bật Ray Tracing và DLSS cực mượt trên màn 2K 165Hz.', 'https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=600&q=80');

-- SEED STORES
INSERT INTO stores (id, name, address, latitude, longitude, phone, opening_hours) VALUES
(1, 'Artifex Nông Lâm - Thủ Đức', 'Số 1 ĐH Nông Lâm, Khu phố 6, P. Linh Trung, TP. Thủ Đức, TP.HCM', 10.870500, 106.791500, '028 3896 0711', '08:00 - 21:30'),
(2, 'Artifex Quận 10', '398 Nguyễn Tri Phương, Phường 4, Quận 10, TP.HCM', 10.763100, 106.666800, '028 3834 5678', '08:30 - 21:30'),
(3, 'Artifex Bình Thạnh', '157 Bạch Đằng, Phường 15, Quận Bình Thạnh, TP.HCM', 10.801600, 106.702900, '028 3511 2233', '08:00 - 21:00'),
(4, 'Artifex Quận 1 Flagship', '92 Cách Mạng Tháng Tám, Phường Bến Thành, Quận 1, TP.HCM', 10.776900, 106.691700, '028 3822 9988', '08:00 - 22:00');

-- SEED ORDERS & ORDER_DETAILS
INSERT INTO orders (id, user_id, total_price, status, receiver_name, receiver_phone, shipping_address) VALUES
(1, 1, 24880000.00, 'COMPLETED', 'Nguyễn Văn A', '0901234567', 'Khu phố 6, P. Linh Trung, TP. Thủ Đức, TP.HCM'),
(2, 1, 3190000.00, 'SHIPPING', 'Nguyễn Văn A', '0901234567', 'Khu phố 6, P. Linh Trung, TP. Thủ Đức, TP.HCM');

INSERT INTO order_details (id, order_id, product_id, quantity, unit_price) VALUES
(1, 1, 1, 1, 7890000.00),
(2, 1, 4, 1, 16990000.00),
(3, 2, 7, 1, 3190000.00);

ALTER TABLE users AUTO_INCREMENT = 100;
ALTER TABLE categories AUTO_INCREMENT = 100;
ALTER TABLE products AUTO_INCREMENT = 100;
ALTER TABLE reviews AUTO_INCREMENT = 100;
ALTER TABLE stores AUTO_INCREMENT = 100;
ALTER TABLE orders AUTO_INCREMENT = 100;
ALTER TABLE order_details AUTO_INCREMENT = 100;
