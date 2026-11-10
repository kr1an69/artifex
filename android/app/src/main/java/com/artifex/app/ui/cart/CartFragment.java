package com.artifex.app.ui.cart;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;
import com.artifex.app.data.local.CartItem;
import com.artifex.app.data.local.CartRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình Giỏ hàng (Room DB) - Phụ trách: Hiếu.
 * Hiển thị sản phẩm đã thêm, tăng/giảm/xóa, tính tổng tiền. Nút "Đặt hàng" sẽ nối sang Checkout ở bước sau.
 */
public class CartFragment extends Fragment {

    private RecyclerView rvCartItems;
    private View layoutCartEmpty, cardCartSummary;
    private TextView tvCartSubtitle, tvCartTotal;
    private MaterialButton btnClearCart, btnCheckout;

    private CartAdapter adapter;
    private CartRepository repository;
    private final List<CartItem> currentItems = new ArrayList<>();

    public CartFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = CartRepository.getInstance(requireContext());

        rvCartItems = view.findViewById(R.id.rvCartItems);
        layoutCartEmpty = view.findViewById(R.id.layoutCartEmpty);
        cardCartSummary = view.findViewById(R.id.cardCartSummary);
        tvCartSubtitle = view.findViewById(R.id.tvCartSubtitle);
        tvCartTotal = view.findViewById(R.id.tvCartTotal);
        btnClearCart = view.findViewById(R.id.btnClearCart);
        btnCheckout = view.findViewById(R.id.btnCheckout);

        adapter = new CartAdapter(new CartAdapter.OnCartActionListener() {
            @Override
            public void onIncrease(CartItem item) {
                repository.setQuantity(item.getProductId(), item.getQuantity() + 1, ok -> loadCart());
            }

            @Override
            public void onDecrease(CartItem item) {
                if (item.getQuantity() <= 1) {
                    confirmRemove(item);
                } else {
                    repository.setQuantity(item.getProductId(), item.getQuantity() - 1, ok -> loadCart());
                }
            }

            @Override
            public void onRemove(CartItem item) {
                confirmRemove(item);
            }
        });
        rvCartItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCartItems.setAdapter(adapter);

        btnClearCart.setOnClickListener(v -> {
            if (currentItems.isEmpty()) return;
            new AlertDialog.Builder(requireContext())
                    .setTitle("Xóa toàn bộ giỏ hàng")
                    .setMessage("Bạn có chắc muốn xóa tất cả sản phẩm trong giỏ hàng không?")
                    .setNegativeButton("Quay lại", null)
                    .setPositiveButton("Xóa tất cả", (d, w) -> repository.clearCart(ok -> loadCart()))
                    .show();
        });

        btnCheckout.setOnClickListener(v -> {
            if (currentItems.isEmpty()) {
                Toast.makeText(requireContext(), "Giỏ hàng đang trống", Toast.LENGTH_SHORT).show();
                return;
            }
            double total = 0;
            for (CartItem item : currentItems) {
                total += item.getPrice() * item.getQuantity();
            }
            CheckoutBottomSheet.newInstance(total, this::loadCart)
                    .show(getChildFragmentManager(), "checkout");
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Tải lại mỗi khi quay lại tab giỏ hàng để thấy sản phẩm vừa được thêm từ màn khác
        loadCart();
    }

    private void confirmRemove(CartItem item) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa sản phẩm")
                .setMessage("Xóa \"" + item.getProductName() + "\" khỏi giỏ hàng?")
                .setNegativeButton("Quay lại", null)
                .setPositiveButton("Xóa", (d, w) -> repository.removeFromCart(item.getProductId(), ok -> loadCart()))
                .show();
    }

    private void loadCart() {
        repository.getCartItems(items -> {
            if (!isAdded()) return;
            currentItems.clear();
            currentItems.addAll(items);
            adapter.updateData(items);
            updateSummary();
        });
    }

    private void updateSummary() {
        boolean empty = currentItems.isEmpty();
        layoutCartEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvCartItems.setVisibility(empty ? View.GONE : View.VISIBLE);
        cardCartSummary.setVisibility(empty ? View.GONE : View.VISIBLE);
        btnClearCart.setVisibility(empty ? View.GONE : View.VISIBLE);

        int totalQty = 0;
        double totalPrice = 0;
        for (CartItem item : currentItems) {
            totalQty += item.getQuantity();
            totalPrice += item.getPrice() * item.getQuantity();
        }
        tvCartSubtitle.setText(totalQty + " sản phẩm");
        tvCartTotal.setText(CartAdapter.formatCurrency(totalPrice));
    }
}
