package com.artifex.app.ui.auth;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;
import com.bumptech.glide.Glide;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Adapter độc lập trong package ui/auth cho lưới "Có thể bạn cũng thích" trên màn hình Hồ sơ.
 */
public class RecommendedProductAdapter extends RecyclerView.Adapter<RecommendedProductAdapter.RecViewHolder> {

    public interface OnRecommendationClickListener {
        void onProductClick(RecommendedProduct product);
    }

    private List<RecommendedProduct> items;
    private final OnRecommendationClickListener listener;
    private final DecimalFormat priceFormatter;

    public RecommendedProductAdapter(List<RecommendedProduct> items, OnRecommendationClickListener listener) {
        this.items = items;
        this.listener = listener;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        this.priceFormatter = new DecimalFormat("#,### đ", symbols);
    }

    @NonNull
    @Override
    public RecViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_auth_recommendation_card, parent, false);
        return new RecViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecViewHolder holder, int position) {
        RecommendedProduct item = items.get(position);
        holder.tvName.setText(item.getName());
        holder.tvSpec.setText(item.getDescription());
        holder.tvPrice.setText(priceFormatter.format(item.getPrice()));

        if (!item.getCategoryName().isEmpty()) {
            holder.tvBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText(item.getCategoryName().toUpperCase(Locale.ROOT));
        } else {
            holder.tvBadge.setVisibility(View.GONE);
        }

        Glide.with(holder.itemView.getContext())
                .load(item.getImageUrl())
                .placeholder(R.color.artifex_border)
                .error(R.color.artifex_border)
                .centerCrop()
                .into(holder.ivProduct);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void updateData(List<RecommendedProduct> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    static class RecViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivProduct;
        final TextView tvBadge;
        final TextView tvName;
        final TextView tvSpec;
        final TextView tvPrice;

        RecViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProduct = itemView.findViewById(R.id.ivRecProduct);
            tvBadge = itemView.findViewById(R.id.tvRecBadge);
            tvName = itemView.findViewById(R.id.tvRecName);
            tvSpec = itemView.findViewById(R.id.tvRecSpec);
            tvPrice = itemView.findViewById(R.id.tvRecPrice);
        }
    }
}
