package com.artifex.app.ui.cart;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.artifex.app.R;
import com.artifex.app.api.RetrofitClient;
import com.artifex.app.data.local.CartItem;
import com.artifex.app.data.local.CartRepository;
import com.artifex.app.ui.auth.SharedPrefManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình Thanh toán / Đặt hàng (Checkout) - Phụ trách: Hiếu.
 * Tự điền sẵn tên, số điện thoại, địa chỉ từ SharedPrefManager (chỉ đọc), gọi POST /api/orders.
 * Đặt hàng thành công thì xóa giỏ hàng Room.
 */
public class CheckoutBottomSheet extends BottomSheetDialogFragment {

    public interface OnOrderPlacedListener {
        void onOrderPlaced();
    }

    private static final String ARG_TOTAL = "arg_total";

    private OnOrderPlacedListener listener;
    private TextInputLayout tilName, tilPhone, tilAddress;
    private TextInputEditText etName, etPhone, etAddress;
    private MaterialButton btnConfirm;

    public static CheckoutBottomSheet newInstance(double total, OnOrderPlacedListener listener) {
        CheckoutBottomSheet sheet = new CheckoutBottomSheet();
        Bundle args = new Bundle();
        args.putDouble(ARG_TOTAL, total);
        sheet.setArguments(args);
        sheet.listener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_checkout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tilName = view.findViewById(R.id.tilReceiverName);
        tilPhone = view.findViewById(R.id.tilReceiverPhone);
        tilAddress = view.findViewById(R.id.tilShippingAddress);
        etName = view.findViewById(R.id.etReceiverName);
        etPhone = view.findViewById(R.id.etReceiverPhone);
        etAddress = view.findViewById(R.id.etShippingAddress);
        btnConfirm = view.findViewById(R.id.btnConfirmOrder);
        TextView tvTotal = view.findViewById(R.id.tvCheckoutTotal);

        double total = getArguments() != null ? getArguments().getDouble(ARG_TOTAL) : 0;
        tvTotal.setText(CartAdapter.formatCurrency(total));

        // Điền sẵn thông tin mặc định của khách (chỉ đọc từ SharedPrefManager, không sửa)
        SharedPrefManager pref = SharedPrefManager.getInstance(requireContext());
        etName.setText(pref.getFullName());
        etPhone.setText(pref.getPhone());
        etAddress.setText(pref.getAddress());

        btnConfirm.setOnClickListener(v -> submitOrder());
    }

    private boolean validate() {
        String name = textOf(etName);
        String phone = textOf(etPhone);
        String address = textOf(etAddress);
        boolean ok = true;

        tilName.setError(name.isEmpty() ? "Vui lòng nhập tên người nhận" : null);
        if (name.isEmpty()) ok = false;

        boolean phoneValid = phone.matches("^\\+?\\d{9,12}$");
        tilPhone.setError(phoneValid ? null : "Số điện thoại không hợp lệ");
        if (!phoneValid) ok = false;

        tilAddress.setError(address.isEmpty() ? "Vui lòng nhập địa chỉ giao hàng" : null);
        if (address.isEmpty()) ok = false;

        return ok;
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void submitOrder() {
        if (!validate()) return;

        btnConfirm.setEnabled(false);
        btnConfirm.setText("Đang đặt hàng...");

        CartRepository.getInstance(requireContext()).getCartItems(items -> {
            if (!isAdded()) return;
            if (items.isEmpty()) {
                Toast.makeText(requireContext(), "Giỏ hàng đang trống", Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            }
            sendOrder(items);
        });
    }

    private void sendOrder(List<CartItem> cartItems) {
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (CartItem item : cartItems) {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", item.getProductId());
            m.put("quantity", item.getQuantity());
            itemList.add(m);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("userId", SharedPrefManager.getInstance(requireContext()).getUserId());
        body.put("receiverName", textOf(etName));
        body.put("receiverPhone", textOf(etPhone));
        body.put("shippingAddress", textOf(etAddress));
        body.put("items", itemList);

        RetrofitClient.getApiService().createOrder(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call,
                                   @NonNull Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    CartRepository.getInstance(requireContext()).clearCart(ok -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Đặt hàng thành công!", Toast.LENGTH_LONG).show();
                        if (listener != null) listener.onOrderPlaced();
                        dismiss();
                    });
                } else {
                    resetButton();
                    Toast.makeText(requireContext(), parseError(response), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                resetButton();
                Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void resetButton() {
        btnConfirm.setEnabled(true);
        btnConfirm.setText("Xác nhận đặt hàng");
    }

    /** Lấy trường "message" mà OrderController trả về khi lỗi (hết hàng, sai sản phẩm...). */
    @SuppressWarnings("unchecked")
    private String parseError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                Map<String, Object> err = new Gson().fromJson(response.errorBody().string(), Map.class);
                if (err != null && err.get("message") != null) {
                    return String.valueOf(err.get("message"));
                }
            }
        } catch (Exception ignored) {
            // dùng thông báo mặc định bên dưới
        }
        return "Không thể đặt hàng (mã lỗi " + response.code() + ")";
    }
}
