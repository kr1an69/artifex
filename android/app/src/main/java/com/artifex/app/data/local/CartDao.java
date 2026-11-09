package com.artifex.app.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CartDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CartItem item);

    @Update
    void update(CartItem item);

    @Delete
    void delete(CartItem item);

    @Query("SELECT * FROM cart_items")
    List<CartItem> getAllCartItems();

    @Query("SELECT * FROM cart_items WHERE productId = :productId LIMIT 1")
    CartItem findByProductId(int productId);

    @Query("UPDATE cart_items SET quantity = :quantity WHERE productId = :productId")
    void updateQuantity(int productId, int quantity);

    @Query("DELETE FROM cart_items WHERE productId = :productId")
    void deleteByProductId(int productId);

    // Tổng tiền toàn giỏ (0 nếu giỏ trống)
    @Query("SELECT IFNULL(SUM(price * quantity), 0) FROM cart_items")
    double getTotalPrice();

    // Tổng số lượng sản phẩm (dùng cho badge giỏ hàng)
    @Query("SELECT IFNULL(SUM(quantity), 0) FROM cart_items")
    int getTotalQuantity();

    @Query("DELETE FROM cart_items")
    void clearCart();
}
