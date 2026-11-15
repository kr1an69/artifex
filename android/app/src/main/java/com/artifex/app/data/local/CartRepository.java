package com.artifex.app.data.local;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp truy cập giỏ hàng (Room) - Phụ trách: Hiếu.
 * Mọi thao tác DB chạy trên 1 luồng nền (ExecutorService), kết quả được đưa về Main Thread qua callback.
 * Màn Catalog/Detail muốn thêm vào giỏ chỉ cần gọi: CartRepository.getInstance(ctx).addToCart(item, null);
 */
public class CartRepository {

    public interface Callback<T> {
        void onResult(T result);
    }

    private static volatile CartRepository instance;

    private final CartDao cartDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private CartRepository(Context context) {
        cartDao = AppDatabase.getInstance(context).cartDao();
    }

    public static CartRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (CartRepository.class) {
                if (instance == null) {
                    instance = new CartRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private <T> void post(@Nullable Callback<T> callback, T result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onResult(result));
        }
    }

    /** Thêm vào giỏ; nếu sản phẩm đã có thì cộng dồn số lượng. */
    public void addToCart(CartItem item, @Nullable Callback<Boolean> callback) {
        executor.execute(() -> {
            CartItem existing = cartDao.findByProductId(item.getProductId());
            if (existing != null) {
                cartDao.updateQuantity(item.getProductId(), existing.getQuantity() + item.getQuantity());
            } else {
                cartDao.insert(item);
            }
            post(callback, true);
        });
    }

    public void getCartItems(Callback<List<CartItem>> callback) {
        executor.execute(() -> post(callback, cartDao.getAllCartItems()));
    }

    /** Đặt số lượng mới; số lượng <= 0 thì xóa khỏi giỏ. */
    public void setQuantity(int productId, int quantity, @Nullable Callback<Boolean> callback) {
        executor.execute(() -> {
            if (quantity <= 0) {
                cartDao.deleteByProductId(productId);
            } else {
                cartDao.updateQuantity(productId, quantity);
            }
            post(callback, true);
        });
    }

    public void removeFromCart(int productId, @Nullable Callback<Boolean> callback) {
        executor.execute(() -> {
            cartDao.deleteByProductId(productId);
            post(callback, true);
        });
    }

    public void clearCart(@Nullable Callback<Boolean> callback) {
        executor.execute(() -> {
            cartDao.clearCart();
            post(callback, true);
        });
    }

    public void getTotalPrice(Callback<Double> callback) {
        executor.execute(() -> post(callback, cartDao.getTotalPrice()));
    }

    public void getTotalQuantity(Callback<Integer> callback) {
        executor.execute(() -> post(callback, cartDao.getTotalQuantity()));
    }
}
