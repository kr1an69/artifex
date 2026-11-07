package com.artifex.app.ui.store;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter cho RecyclerView hiển thị danh sách Lịch sử đơn hàng (Giai đoạn 5 - Bạn 5).
 * Tuân thủ 100% Design System: Sử dụng Semantic Color Tokens (@color/artifex_*).
 */
public class OrderHistoryAdapter extends RecyclerView.Adapter<OrderHistoryAdapter.OrderViewHolder> {

    public interface OnOrderActionListener {
        void onViewOrderDetail(OrderModel order);
        void onCancelOrder(OrderModel order, int position);
    }

    private final List<OrderModel> orderList;
    private final OnOrderActionListener listener;
    private final DecimalFormat priceFormatter;

    public OrderHistoryAdapter(List<OrderModel> initialOrders, OnOrderActionListener listener) {
        this.orderList = initialOrders != null ? new ArrayList<>(initialOrders) : new ArrayList<>();
        this.listener = listener;

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        this.priceFormatter = new DecimalFormat("#,### đ", symbols);
    }

    public void updateData(List<OrderModel> newOrders) {
        this.orderList.clear();
        if (newOrders != null) {
            this.orderList.addAll(newOrders);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_card, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        OrderModel order = orderList.get(position);
        Context context = holder.itemView.getContext();

        // 1. Mã đơn hàng & Ngày tạo
        long orderId = order.getId() != null ? order.getId() : (position + 1);
        holder.tvOrderCode.setText(String.format(Locale.US, "#ART-%04d", orderId));
        holder.tvOrderDate.setText(formatOrderDate(order.getCreatedAt()));

        // 2. Huy hiệu trạng thái (Badge màu sắc chuẩn Design System)
        applyStatusBadgeStyle(context, order.getStatus(), holder.cardStatusBadge, holder.tvStatusBadge);

        // 3. Hiển thị thông tin linh kiện đầu tiên trong đơn
        List<OrderModel.OrderItemModel> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            OrderModel.OrderItemModel firstItem = items.get(0);
            String productName = firstItem.getProduct() != null
                    ? firstItem.getProduct().getName()
                    : "Linh kiện PC Artifex";
            String imageUrl = firstItem.getProduct() != null
                    ? firstItem.getProduct().getImageUrl()
                    : null;

            holder.tvFirstProductName.setText(productName);
            holder.tvFirstProductMeta.setText(
                    "Số lượng: x" + firstItem.getQuantity() + " • Đơn giá: " + formatCurrency(firstItem.getUnitPrice())
            );

            Glide.with(context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_nav_catalog)
                    .error(R.drawable.ic_nav_catalog)
                    .centerCrop()
                    .into(holder.ivFirstProductThumb);

            if (items.size() > 1) {
                holder.tvMoreItemsBadge.setVisibility(View.VISIBLE);
                holder.tvMoreItemsBadge.setText("+ " + (items.size() - 1) + " linh kiện khác trong đơn hàng");
            } else {
                holder.tvMoreItemsBadge.setVisibility(View.GONE);
            }
        } else {
            holder.tvFirstProductName.setText("Đơn hàng linh kiện Artifex");
            holder.tvFirstProductMeta.setText("Số lượng: x1");
            holder.tvMoreItemsBadge.setVisibility(View.GONE);
            holder.ivFirstProductThumb.setImageResource(R.drawable.ic_nav_catalog);
        }

        // 4. Tóm tắt người nhận & địa chỉ giao hàng
        String shippingInfo = order.getReceiverName();
        if (!order.getReceiverPhone().isEmpty()) {
            shippingInfo += " (" + order.getReceiverPhone() + ")";
        }
        if (!order.getShippingAddress().isEmpty()) {
            shippingInfo += " • " + order.getShippingAddress();
        }
        holder.tvOrderShippingSummary.setText(shippingInfo);

        // 5. Tổng số lượng & Tổng tiền thanh toán
        int totalQty = Math.max(1, order.getTotalItemCount());
        holder.tvTotalItemsCountLabel.setText("Tổng thanh toán (" + totalQty + " sản phẩm)");
        holder.tvOrderTotalPrice.setText(formatCurrency(order.getTotalPrice()));

        // 6. Nút Hủy đơn hàng: CHỈ kích hoạt khi đơn ở trạng thái PENDING
        boolean isPending = "PENDING".equalsIgnoreCase(order.getStatus());
        holder.btnCancelOrder.setVisibility(isPending ? View.VISIBLE : View.GONE);

        // 7. Gắn sự kiện Click
        View.OnClickListener detailClickListener = v -> {
            if (listener != null) {
                listener.onViewOrderDetail(order);
            }
        };
        holder.cardOrderItem.setOnClickListener(detailClickListener);
        holder.btnOrderDetail.setOnClickListener(detailClickListener);

        holder.btnCancelOrder.setOnClickListener(v -> {
            if (listener != null) {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    listener.onCancelOrder(order, currentPos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    /**
     * Thiết lập màu sắc và nhãn tiếng Việt cho Badge trạng thái đơn hàng
     * dựa trên token ngữ nghĩa trong DESIGN_SYSTEM.md.
     */
    public static void applyStatusBadgeStyle(Context context, String rawStatus,
                                             MaterialCardView cardBadge, TextView tvBadge) {
        String status = rawStatus != null ? rawStatus.toUpperCase(Locale.ROOT) : "PENDING";
        int colorRes;
        String label;

        switch (status) {
            case "SHIPPING":
                colorRes = R.color.artifex_primary;
                label = "Đang vận chuyển";
                break;
            case "COMPLETED":
                colorRes = R.color.artifex_success;
                label = "Giao thành công";
                break;
            case "CANCELLED":
                colorRes = R.color.artifex_error;
                label = "Đã hủy";
                break;
            case "PENDING":
            default:
                colorRes = R.color.artifex_accent;
                label = "Chờ xác nhận";
                break;
        }

        int resolvedColor = ContextCompat.getColor(context, colorRes);
        cardBadge.setStrokeColor(resolvedColor);
        tvBadge.setTextColor(resolvedColor);
        tvBadge.setText(label);
    }

    public String formatCurrency(double amount) {
        return priceFormatter.format(amount);
    }

    /**
     * Format chuỗi thời gian ISO từ Backend (VD: "2026-10-31T14:30:00")
     * sang định dạng thân thiện "31/10/2026 • 14:30".
     */
    public static String formatOrderDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return "Hôm nay";
        }
        try {
            String clean = rawDate.replace("T", " ").trim();
            String[] parts = clean.split(" ");
            String datePart = parts[0]; // yyyy-MM-dd
            String timePart = parts.length > 1 ? parts[1] : "";
            if (timePart.length() >= 5) {
                timePart = timePart.substring(0, 5); // HH:mm
            }
            String[] dateTokens = datePart.split("-");
            if (dateTokens.length == 3) {
                String formattedDate = dateTokens[2] + "/" + dateTokens[1] + "/" + dateTokens[0];
                return timePart.isEmpty() ? formattedDate : (formattedDate + " • " + timePart);
            }
            return clean;
        } catch (Exception e) {
            return rawDate;
        }
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardOrderItem;
        final TextView tvOrderCode;
        final TextView tvOrderDate;
        final MaterialCardView cardStatusBadge;
        final TextView tvStatusBadge;
        final ImageView ivFirstProductThumb;
        final TextView tvFirstProductName;
        final TextView tvFirstProductMeta;
        final TextView tvMoreItemsBadge;
        final TextView tvOrderShippingSummary;
        final TextView tvTotalItemsCountLabel;
        final TextView tvOrderTotalPrice;
        final MaterialButton btnOrderDetail;
        final MaterialButton btnCancelOrder;

        OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            cardOrderItem = itemView.findViewById(R.id.cardOrderItem);
            tvOrderCode = itemView.findViewById(R.id.tvOrderCode);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            cardStatusBadge = itemView.findViewById(R.id.cardStatusBadge);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            ivFirstProductThumb = itemView.findViewById(R.id.ivFirstProductThumb);
            tvFirstProductName = itemView.findViewById(R.id.tvFirstProductName);
            tvFirstProductMeta = itemView.findViewById(R.id.tvFirstProductMeta);
            tvMoreItemsBadge = itemView.findViewById(R.id.tvMoreItemsBadge);
            tvOrderShippingSummary = itemView.findViewById(R.id.tvOrderShippingSummary);
            tvTotalItemsCountLabel = itemView.findViewById(R.id.tvTotalItemsCountLabel);
            tvOrderTotalPrice = itemView.findViewById(R.id.tvOrderTotalPrice);
            btnOrderDetail = itemView.findViewById(R.id.btnOrderDetail);
            btnCancelOrder = itemView.findViewById(R.id.btnCancelOrder);
        }
    }
}
