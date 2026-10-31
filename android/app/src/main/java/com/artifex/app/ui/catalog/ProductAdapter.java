package com.artifex.app.ui.catalog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.artifex.app.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.google.android.material.card.MaterialCardView;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    private List<CatalogFragment.ProductItem> products;
    private OnProductClickListener listener;
    private DecimalFormat priceFormatter;

    public interface OnProductClickListener {
        void onProductClick(CatalogFragment.ProductItem product);
    }

    public ProductAdapter(List<CatalogFragment.ProductItem> products, OnProductClickListener listener) {
        this.products = products;
        this.listener = listener;
        
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        priceFormatter = new DecimalFormat("#,### đ", symbols);
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_card, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        CatalogFragment.ProductItem product = products.get(position);
        
        holder.tvProductName.setText(product.getName());
        holder.tvProductSpec.setText(product.getShortSpec());
        holder.tvProductPrice.setText(priceFormatter.format(product.getPrice()));
        
        if (product.getDiscountBadge() != null && !product.getDiscountBadge().isEmpty()) {
            holder.cardBadge.setVisibility(View.VISIBLE);
            holder.tvBadge.setText(product.getDiscountBadge());
        } else {
            holder.cardBadge.setVisibility(View.GONE);
        }

        // Tải ảnh qua Glide với placeholder
        Glide.with(holder.itemView.getContext())
                .load(product.getImageUrl())
                .placeholder(R.color.artifex_border)
                .error(R.color.artifex_border)
                .transform(new CenterCrop())
                .into(holder.ivProduct);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductClick(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return products != null ? products.size() : 0;
    }

    public void updateData(List<CatalogFragment.ProductItem> newProducts) {
        this.products = newProducts;
        notifyDataSetChanged();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProduct;
        MaterialCardView cardBadge;
        TextView tvBadge;
        TextView tvProductName;
        TextView tvProductSpec;
        TextView tvProductPrice;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProduct = itemView.findViewById(R.id.iv_product);
            cardBadge = itemView.findViewById(R.id.card_badge);
            tvBadge = itemView.findViewById(R.id.tv_badge);
            tvProductName = itemView.findViewById(R.id.tv_product_name);
            tvProductSpec = itemView.findViewById(R.id.tv_product_spec);
            tvProductPrice = itemView.findViewById(R.id.tv_product_price);
        }
    }
}