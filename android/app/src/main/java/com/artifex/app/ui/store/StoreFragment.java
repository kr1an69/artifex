package com.artifex.app.ui.store;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;
import com.artifex.app.api.RetrofitClient;
import com.artifex.app.ui.auth.SharedPrefManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình Lịch sử Đơn hàng (Order History - Giai đoạn 5 - Phụ trách: Bạn 5).
 * Hỗ trợ:
 * 1. Hiển thị trực tiếp ở Tab 2 trên BottomNavigationView của MainActivity.
 * 2. Nhúng vào OrderHistoryActivity khi người dùng bấm từ mục "Đơn mua" bên AuthFragment.
 */
public class StoreFragment extends Fragment {

    public static final String ARG_INITIAL_FILTER = "arg_initial_filter";
    public static final String ARG_SHOW_BACK_BUTTON = "arg_show_back_button";

    public static final String FILTER_ALL = "ALL";
    public static final String FILTER_PENDING = "PENDING";
    public static final String FILTER_SHIPPING = "SHIPPING";
    public static final String FILTER_COMPLETED = "COMPLETED";
    public static final String FILTER_CANCELLED = "CANCELLED";

    // Views
    private View btnBackOrderHistory;
    private TextView tvOrderHeaderSubtitle;
    private MaterialButton btnRefreshOrders;
    private MaterialButton chipFilterAll, chipFilterPending, chipFilterShipping, chipFilterCompleted, chipFilterCancelled;
    private View cardOfflineNotice;
    private RecyclerView rvOrderHistory;
    private View layoutOrderEmptyState;
    private TextView tvOrderEmptyTitle, tvOrderEmptySubtitle;
    private ProgressBar progressOrderLoading;

    // Data & State
    private OrderHistoryAdapter orderAdapter;
    private final List<OrderModel> allOrders = new ArrayList<>();
    private String currentFilterStatus = FILTER_ALL;
    private boolean isOfflineDemoMode = false;

    public StoreFragment() {
        // Required empty public constructor
    }

    /**
     * Khởi tạo StoreFragment kèm bộ lọc trạng thái ban đầu và tùy chọn hiển thị nút Back.
     */
    public static StoreFragment newInstance(String initialFilter, boolean showBackButton) {
        StoreFragment fragment = new StoreFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_FILTER, initialFilter != null ? initialFilter : FILTER_ALL);
        args.putBoolean(ARG_SHOW_BACK_BUTTON, showBackButton);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentFilterStatus = getArguments().getString(ARG_INITIAL_FILTER, FILTER_ALL);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_store, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupHeaderAndBackButton();
        setupRecyclerView();
        setupFilterChips();

        // Tải danh sách đơn hàng từ Backend Spring Boot
        fetchOrdersFromBackend();
    }

    private void bindViews(View view) {
        btnBackOrderHistory = view.findViewById(R.id.btnBackOrderHistory);
        tvOrderHeaderSubtitle = view.findViewById(R.id.tvOrderHeaderSubtitle);
        btnRefreshOrders = view.findViewById(R.id.btnRefreshOrders);

        chipFilterAll = view.findViewById(R.id.chipFilterAll);
        chipFilterPending = view.findViewById(R.id.chipFilterPending);
        chipFilterShipping = view.findViewById(R.id.chipFilterShipping);
        chipFilterCompleted = view.findViewById(R.id.chipFilterCompleted);
        chipFilterCancelled = view.findViewById(R.id.chipFilterCancelled);

        cardOfflineNotice = view.findViewById(R.id.cardOfflineNotice);
        rvOrderHistory = view.findViewById(R.id.rvOrderHistory);
        layoutOrderEmptyState = view.findViewById(R.id.layoutOrderEmptyState);
        tvOrderEmptyTitle = view.findViewById(R.id.tvOrderEmptyTitle);
        tvOrderEmptySubtitle = view.findViewById(R.id.tvOrderEmptySubtitle);
        progressOrderLoading = view.findViewById(R.id.progressOrderLoading);
    }

    private void setupHeaderAndBackButton() {
        boolean showBack = getArguments() != null && getArguments().getBoolean(ARG_SHOW_BACK_BUTTON, false);
        btnBackOrderHistory.setVisibility(showBack ? View.VISIBLE : View.GONE);
        btnBackOrderHistory.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        btnRefreshOrders.setOnClickListener(v -> fetchOrdersFromBackend());
    }

    private void setupRecyclerView() {
        orderAdapter = new OrderHistoryAdapter(new ArrayList<>(), new OrderHistoryAdapter.OnOrderActionListener() {
            @Override
            public void onViewOrderDetail(OrderModel order) {
                showOrderDetailBottomSheet(order);
            }

            @Override
            public void onCancelOrder(OrderModel order, int position) {
                showConfirmCancelDialog(order, null);
            }
        });

        rvOrderHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrderHistory.setAdapter(orderAdapter);
    }

    private void setupFilterChips() {
        chipFilterAll.setOnClickListener(v -> applyFilter(FILTER_ALL));
        chipFilterPending.setOnClickListener(v -> applyFilter(FILTER_PENDING));
        chipFilterShipping.setOnClickListener(v -> applyFilter(FILTER_SHIPPING));
        chipFilterCompleted.setOnClickListener(v -> applyFilter(FILTER_COMPLETED));
        chipFilterCancelled.setOnClickListener(v -> applyFilter(FILTER_CANCELLED));

        updateFilterChipStyles();
    }

    /**
     * Gọi API GET /api/orders/user/{userId} từ OrderHistoryController.
     * Nếu Backend chưa bật, tự động nạp dữ liệu mẫu (Offline Demo) để thuận tiện kiểm thử giao diện.
     */
    private void fetchOrdersFromBackend() {
        if (!isAdded()) return;

        SharedPrefManager pref = SharedPrefManager.getInstance(requireContext());
        Long userId = pref.getUserId() != null ? pref.getUserId() : 1L;
        String fullName = pref.getFullName();

        progressOrderLoading.setVisibility(View.VISIBLE);
        layoutOrderEmptyState.setVisibility(View.GONE);

        RetrofitClient.getApiService().getOrderHistoryByUser(userId).enqueue(new Callback<List<OrderModel>>() {
            @Override
            public void onResponse(@NonNull Call<List<OrderModel>> call, @NonNull Response<List<OrderModel>> response) {
                if (!isAdded()) return;
                progressOrderLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    isOfflineDemoMode = false;
                    cardOfflineNotice.setVisibility(View.GONE);

                    allOrders.clear();
                    allOrders.addAll(response.body());

                    tvOrderHeaderSubtitle.setText("Khách hàng: " + fullName + " (ID #" + userId + ") • " + allOrders.size() + " đơn hàng");
                    updateFilterChipCounts();
                    applyFilter(currentFilterStatus);
                } else {
                    loadOfflineDemoOrders(fullName, userId);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<OrderModel>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                progressOrderLoading.setVisibility(View.GONE);
                loadOfflineDemoOrders(fullName, userId);
            }
        });
    }

    /**
     * Dữ liệu mẫu chuẩn khớp với seed_data.sql khi chạy máy ảo mà chưa bật Spring Boot Server.
     * Bao gồm đầy đủ 4 trạng thái (PENDING, SHIPPING, COMPLETED, CANCELLED) để test toàn diện UI.
     */
    private void loadOfflineDemoOrders(String fullName, Long userId) {
        isOfflineDemoMode = true;
        cardOfflineNotice.setVisibility(View.VISIBLE);
        allOrders.clear();

        String address = "Khu phố 6, P. Linh Trung, TP. Thủ Đức, TP.HCM";
        String phone = "0901234567";

        // Đơn 1: Trạng thái PENDING (Để test tính năng Hủy đơn hàng)
        OrderModel.OrderItemModel pendingItem1 = new OrderModel.OrderItemModel(
                101L,
                new OrderModel.ProductInfo(2L, "AMD Ryzen 7 7800X3D (8 Nhân 16 Luồng, 3D V-Cache)", 10490000,
                        "https://images.unsplash.com/photo-1555680202-c86f0e12f086?auto=format&fit=crop&w=800&q=80", ""),
                1, 10490000
        );
        OrderModel.OrderItemModel pendingItem2 = new OrderModel.OrderItemModel(
                102L,
                new OrderModel.ProductInfo(12L, "Mainboard MSI MAG B650 TOMAHAWK WIFI", 5190000,
                        "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80", ""),
                1, 5190000
        );
        allOrders.add(new OrderModel(
                3L, userId, 15680000, FILTER_PENDING,
                fullName, phone, address, "2026-10-31T20:15:00",
                Arrays.asList(pendingItem1, pendingItem2)
        ));

        // Đơn 2: Trạng thái SHIPPING (Khớp seed_data.sql)
        OrderModel.OrderItemModel shippingItem = new OrderModel.OrderItemModel(
                3L,
                new OrderModel.ProductInfo(7L, "Corsair Vengeance RGB DDR5 32GB (2x16GB) 6000MHz", 3190000,
                        "https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80", ""),
                1, 3190000
        );
        allOrders.add(new OrderModel(
                2L, userId, 3190000, FILTER_SHIPPING,
                fullName, phone, address, "2026-10-30T15:40:00",
                Collections.singletonList(shippingItem)
        ));

        // Đơn 3: Trạng thái COMPLETED (Khớp seed_data.sql)
        OrderModel.OrderItemModel completedItem1 = new OrderModel.OrderItemModel(
                1L,
                new OrderModel.ProductInfo(1L, "Intel Core i5-13600K (14 Nhân 20 Luồng, 5.1 GHz)", 7890000,
                        "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=800&q=80", ""),
                1, 7890000
        );
        OrderModel.OrderItemModel completedItem2 = new OrderModel.OrderItemModel(
                2L,
                new OrderModel.ProductInfo(4L, "NVIDIA GeForce RTX 4070 Super 12GB GDDR6X", 16990000,
                        "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=800&q=80", ""),
                1, 16990000
        );
        allOrders.add(new OrderModel(
                1L, userId, 24880000, FILTER_COMPLETED,
                fullName, phone, address, "2026-10-25T09:20:00",
                Arrays.asList(completedItem1, completedItem2)
        ));

        // Đơn 4: Trạng thái CANCELLED
        OrderModel.OrderItemModel cancelledItem = new OrderModel.OrderItemModel(
                104L,
                new OrderModel.ProductInfo(9L, "SSD Samsung 990 Pro 1TB NVMe M.2 PCIe Gen 4.0", 2890000,
                        "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=800&q=80", ""),
                1, 2890000
        );
        allOrders.add(new OrderModel(
                4L, userId, 2890000, FILTER_CANCELLED,
                fullName, phone, address, "2026-10-20T11:05:00",
                Collections.singletonList(cancelledItem)
        ));

        tvOrderHeaderSubtitle.setText("Khách hàng: " + fullName + " • " + allOrders.size() + " đơn hàng (Demo)");
        updateFilterChipCounts();
        applyFilter(currentFilterStatus);
    }

    /**
     * Lọc danh sách đơn hàng theo trạng thái được chọn trên thanh Chip.
     */
    private void applyFilter(String filterStatus) {
        this.currentFilterStatus = filterStatus != null ? filterStatus : FILTER_ALL;
        updateFilterChipStyles();

        List<OrderModel> filtered = new ArrayList<>();
        for (OrderModel order : allOrders) {
            if (FILTER_ALL.equalsIgnoreCase(currentFilterStatus)
                    || currentFilterStatus.equalsIgnoreCase(order.getStatus())) {
                filtered.add(order);
            }
        }

        orderAdapter.updateData(filtered);

        if (filtered.isEmpty()) {
            rvOrderHistory.setVisibility(View.GONE);
            layoutOrderEmptyState.setVisibility(View.VISIBLE);
            tvOrderEmptyTitle.setText("Không có đơn hàng nào");
            tvOrderEmptySubtitle.setText("Hiện chưa có đơn hàng nào ở trạng thái \"" + getVietnameseFilterName(currentFilterStatus) + "\".");
        } else {
            layoutOrderEmptyState.setVisibility(View.GONE);
            rvOrderHistory.setVisibility(View.VISIBLE);
        }
    }

    private String getVietnameseFilterName(String status) {
        switch (status.toUpperCase(Locale.ROOT)) {
            case FILTER_PENDING:
                return "Chờ xác nhận";
            case FILTER_SHIPPING:
                return "Đang vận chuyển";
            case FILTER_COMPLETED:
                return "Hoàn thành";
            case FILTER_CANCELLED:
                return "Đã hủy";
            default:
                return "Tất cả";
        }
    }

    private void updateFilterChipCounts() {
        int pendingCount = 0;
        int shippingCount = 0;
        int completedCount = 0;
        int cancelledCount = 0;

        for (OrderModel o : allOrders) {
            String st = o.getStatus().toUpperCase(Locale.ROOT);
            switch (st) {
                case FILTER_PENDING:
                    pendingCount++;
                    break;
                case FILTER_SHIPPING:
                    shippingCount++;
                    break;
                case FILTER_COMPLETED:
                    completedCount++;
                    break;
                case FILTER_CANCELLED:
                    cancelledCount++;
                    break;
            }
        }

        chipFilterAll.setText("Tất cả (" + allOrders.size() + ")");
        chipFilterPending.setText("Chờ xác nhận (" + pendingCount + ")");
        chipFilterShipping.setText("Đang vận chuyển (" + shippingCount + ")");
        chipFilterCompleted.setText("Hoàn thành (" + completedCount + ")");
        chipFilterCancelled.setText("Đã hủy (" + cancelledCount + ")");
    }

    /**
     * Đổi màu các nút Chip theo chuẩn Design System (@color/artifex_primary khi Active).
     */
    private void updateFilterChipStyles() {
        if (!isAdded()) return;
        styleSingleChip(chipFilterAll, FILTER_ALL.equalsIgnoreCase(currentFilterStatus));
        styleSingleChip(chipFilterPending, FILTER_PENDING.equalsIgnoreCase(currentFilterStatus));
        styleSingleChip(chipFilterShipping, FILTER_SHIPPING.equalsIgnoreCase(currentFilterStatus));
        styleSingleChip(chipFilterCompleted, FILTER_COMPLETED.equalsIgnoreCase(currentFilterStatus));
        styleSingleChip(chipFilterCancelled, FILTER_CANCELLED.equalsIgnoreCase(currentFilterStatus));
    }

    private void styleSingleChip(MaterialButton chip, boolean isSelected) {
        int bgColor = ContextCompat.getColor(requireContext(),
                isSelected ? R.color.artifex_primary : R.color.artifex_surface_variant);
        int textColor = ContextCompat.getColor(requireContext(),
                isSelected ? R.color.artifex_on_primary : R.color.artifex_text_secondary);
        int strokeColor = ContextCompat.getColor(requireContext(),
                isSelected ? R.color.artifex_primary : R.color.artifex_border);

        chip.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        chip.setTextColor(textColor);
        chip.setStrokeColor(ColorStateList.valueOf(strokeColor));
        chip.setStrokeWidth(2);
    }

    /**
     * Hiển thị AlertDialog xác nhận hủy đơn hàng -> Gọi API PUT /api/orders/{id}/cancel
     */
    private void showConfirmCancelDialog(OrderModel order, @Nullable BottomSheetDialog openedBottomSheet) {
        if (!isAdded()) return;

        long orderId = order.getId() != null ? order.getId() : 0L;
        String orderCode = String.format(Locale.US, "#ART-%04d", orderId);

        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận hủy đơn hàng")
                .setMessage("Bạn có chắc chắn muốn hủy đơn hàng " + orderCode + " không?\n\nLưu ý: Sau khi hủy, trạng thái đơn sẽ chuyển sang Đã hủy và không thể hoàn tác.")
                .setNegativeButton("Quay lại", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Đồng ý hủy", (dialog, which) -> {
                    executeCancelOrder(order, orderCode, openedBottomSheet);
                })
                .show();
    }

    private void executeCancelOrder(OrderModel order, String orderCode, @Nullable BottomSheetDialog openedBottomSheet) {
        if (isOfflineDemoMode) {
            // Chế độ Offline Demo: Cập nhật trạng thái ngay lập tức trên UI
            order.setStatus(FILTER_CANCELLED);
            if (openedBottomSheet != null && openedBottomSheet.isShowing()) {
                openedBottomSheet.dismiss();
            }
            updateFilterChipCounts();
            applyFilter(currentFilterStatus);
            Toast.makeText(requireContext(), "Đã hủy đơn hàng " + orderCode + " thành công!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Chế độ Online: Gọi API PUT /api/orders/{orderId}/cancel lên Spring Boot
        progressOrderLoading.setVisibility(View.VISIBLE);
        RetrofitClient.getApiService().cancelOrder(order.getId()).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                progressOrderLoading.setVisibility(View.GONE);

                if (response.isSuccessful()) {
                    order.setStatus(FILTER_CANCELLED);
                    if (openedBottomSheet != null && openedBottomSheet.isShowing()) {
                        openedBottomSheet.dismiss();
                    }
                    updateFilterChipCounts();
                    applyFilter(currentFilterStatus);
                    Toast.makeText(requireContext(), "Hủy đơn hàng " + orderCode + " thành công!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Không thể hủy đơn hàng (Mã lỗi: " + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                progressOrderLoading.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Lỗi kết nối máy chủ khi hủy đơn: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Hiển thị BottomSheet chi tiết đơn hàng (Danh sách đầy đủ các món linh kiện, người nhận, tổng tiền).
     */
    private void showOrderDetailBottomSheet(OrderModel order) {
        if (!isAdded()) return;

        BottomSheetDialog bottomSheet = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_order_detail, null, false);
        bottomSheet.setContentView(sheetView);

        TextView tvDetailOrderCode = sheetView.findViewById(R.id.tvDetailOrderCode);
        TextView tvDetailOrderDate = sheetView.findViewById(R.id.tvDetailOrderDate);
        MaterialCardView cardDetailStatusBadge = sheetView.findViewById(R.id.cardDetailStatusBadge);
        TextView tvDetailStatusBadge = sheetView.findViewById(R.id.tvDetailStatusBadge);
        TextView tvDetailReceiverInfo = sheetView.findViewById(R.id.tvDetailReceiverInfo);
        TextView tvDetailShippingAddress = sheetView.findViewById(R.id.tvDetailShippingAddress);
        TextView tvDetailProductsHeader = sheetView.findViewById(R.id.tvDetailProductsHeader);
        LinearLayout layoutDetailProductItems = sheetView.findViewById(R.id.layoutDetailProductItems);
        TextView tvDetailSummaryLabel = sheetView.findViewById(R.id.tvDetailSummaryLabel);
        TextView tvDetailTotalPrice = sheetView.findViewById(R.id.tvDetailTotalPrice);
        MaterialButton btnDetailClose = sheetView.findViewById(R.id.btnDetailClose);
        MaterialButton btnDetailCancelOrder = sheetView.findViewById(R.id.btnDetailCancelOrder);

        long orderId = order.getId() != null ? order.getId() : 1L;
        tvDetailOrderCode.setText(String.format(Locale.US, "#ART-%04d", orderId));
        tvDetailOrderDate.setText("• " + OrderHistoryAdapter.formatOrderDate(order.getCreatedAt()));

        OrderHistoryAdapter.applyStatusBadgeStyle(
                requireContext(), order.getStatus(), cardDetailStatusBadge, tvDetailStatusBadge
        );

        String receiverText = order.getReceiverName();
        if (!order.getReceiverPhone().isEmpty()) {
            receiverText += " • " + order.getReceiverPhone();
        }
        tvDetailReceiverInfo.setText(receiverText);
        tvDetailShippingAddress.setText(order.getShippingAddress());

        // Render danh sách từng món linh kiện trong đơn hàng
        List<OrderModel.OrderItemModel> items = order.getItems();
        tvDetailProductsHeader.setText("Danh sách linh kiện đã đặt (" + items.size() + " món)");
        layoutDetailProductItems.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (OrderModel.OrderItemModel item : items) {
            View itemView = inflater.inflate(R.layout.item_order_detail_product, layoutDetailProductItems, false);

            ImageView ivImg = itemView.findViewById(R.id.ivDetailProductImg);
            TextView tvName = itemView.findViewById(R.id.tvDetailProductName);
            TextView tvQtyAndUnit = itemView.findViewById(R.id.tvDetailProductQtyAndUnit);
            TextView tvSubtotal = itemView.findViewById(R.id.tvDetailProductSubtotal);

            String pName = item.getProduct() != null ? item.getProduct().getName() : "Linh kiện PC Artifex";
            String pImg = item.getProduct() != null ? item.getProduct().getImageUrl() : null;
            double subtotal = item.getUnitPrice() * item.getQuantity();

            tvName.setText(pName);
            tvQtyAndUnit.setText("SL: x" + item.getQuantity() + " • " + orderAdapter.formatCurrency(item.getUnitPrice()));
            tvSubtotal.setText(orderAdapter.formatCurrency(subtotal));

            Glide.with(requireContext())
                    .load(pImg)
                    .placeholder(R.drawable.ic_nav_catalog)
                    .error(R.drawable.ic_nav_catalog)
                    .centerCrop()
                    .into(ivImg);

            layoutDetailProductItems.addView(itemView);
        }

        tvDetailSummaryLabel.setText("Tổng thanh toán (" + Math.max(1, order.getTotalItemCount()) + " sản phẩm)");
        tvDetailTotalPrice.setText(orderAdapter.formatCurrency(order.getTotalPrice()));

        btnDetailClose.setOnClickListener(v -> bottomSheet.dismiss());

        if (FILTER_PENDING.equalsIgnoreCase(order.getStatus())) {
            btnDetailCancelOrder.setVisibility(View.VISIBLE);
            btnDetailCancelOrder.setOnClickListener(v -> showConfirmCancelDialog(order, bottomSheet));
        } else {
            btnDetailCancelOrder.setVisibility(View.GONE);
        }

        bottomSheet.show();
    }
}
