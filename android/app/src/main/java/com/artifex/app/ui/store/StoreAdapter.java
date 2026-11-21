package com.artifex.app.ui.store;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView Adapter hiển thị danh sách Showroom Artifex (Phụ trách: Bạn 5).
 * Tự động đánh dấu nhãn "Gần bạn nhất" cho Showroom đứng đầu sau khi ExecutorService sắp xếp.
 */
public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.StoreViewHolder> {

    public interface OnStoreActionListener {
        void onFocusStoreOnMap(StoreModel store);
        void onOpenDirections(StoreModel store);
        void onCallStore(StoreModel store);
    }

    private final List<StoreModel> storeList = new ArrayList<>();
    private final OnStoreActionListener listener;

    public StoreAdapter(List<StoreModel> initialList, OnStoreActionListener listener) {
        if (initialList != null) {
            this.storeList.addAll(initialList);
        }
        this.listener = listener;
    }

    public void updateData(List<StoreModel> newStores) {
        this.storeList.clear();
        if (newStores != null) {
            this.storeList.addAll(newStores);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_store_card, parent, false);
        return new StoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
        StoreModel store = storeList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvStoreName.setText(store.getName());
        holder.tvStoreAddress.setText(store.getAddress());
        holder.tvStoreHours.setText(store.getOpeningHours());
        holder.tvStorePhone.setText(store.getPhone());

        // Hiển thị khoảng cách đã tính bởi ExecutorService và nhãn "Gần bạn nhất" cho vị trí đầu tiên
        boolean hasDistance = store.getDistanceKm() >= 0;
        boolean isNearest = (position == 0 && hasDistance);

        holder.cardNearestBadge.setVisibility(isNearest ? View.VISIBLE : View.GONE);

        if (hasDistance) {
            holder.tvStoreDistance.setText(String.format(Locale.US, "%.2f km", store.getDistanceKm()));
        } else {
            holder.tvStoreDistance.setText("Chưa định vị");
        }

        // Đổi màu viền thẻ nếu là Showroom gần nhất (Tuân thủ Semantic Tokens của DESIGN_SYSTEM.md)
        int strokeColor = ContextCompat.getColor(context,
                isNearest ? R.color.artifex_primary : R.color.artifex_border);
        holder.cardStoreItem.setStrokeColor(strokeColor);
        holder.cardStoreItem.setStrokeWidth(isNearest ? 3 : 2);

        holder.cardStoreItem.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFocusStoreOnMap(store);
            }
        });

        holder.btnFocusMap.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFocusStoreOnMap(store);
            }
        });

        holder.btnDirections.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpenDirections(store);
            }
        });

        holder.btnCallStore.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCallStore(store);
            }
        });
    }

    @Override
    public int getItemCount() {
        return storeList.size();
    }

    static class StoreViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardStoreItem;
        final MaterialCardView cardNearestBadge;
        final MaterialCardView cardDistanceBadge;
        final TextView tvStoreName;
        final TextView tvStoreDistance;
        final TextView tvStoreAddress;
        final TextView tvStoreHours;
        final TextView tvStorePhone;
        final MaterialButton btnFocusMap;
        final MaterialButton btnDirections;
        final MaterialButton btnCallStore;

        StoreViewHolder(@NonNull View itemView) {
            super(itemView);
            cardStoreItem = itemView.findViewById(R.id.cardStoreItem);
            cardNearestBadge = itemView.findViewById(R.id.cardNearestBadge);
            cardDistanceBadge = itemView.findViewById(R.id.cardDistanceBadge);
            tvStoreName = itemView.findViewById(R.id.tvStoreName);
            tvStoreDistance = itemView.findViewById(R.id.tvStoreDistance);
            tvStoreAddress = itemView.findViewById(R.id.tvStoreAddress);
            tvStoreHours = itemView.findViewById(R.id.tvStoreHours);
            tvStorePhone = itemView.findViewById(R.id.tvStorePhone);
            btnFocusMap = itemView.findViewById(R.id.btnFocusMap);
            btnDirections = itemView.findViewById(R.id.btnDirections);
            btnCallStore = itemView.findViewById(R.id.btnCallStore);
        }
    }
}
