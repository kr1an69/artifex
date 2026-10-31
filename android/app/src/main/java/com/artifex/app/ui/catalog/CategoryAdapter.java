package com.artifex.app.ui.catalog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.artifex.app.R;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private List<CatalogFragment.CategoryItem> categories;
    private int selectedPosition = 0;
    private OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onCategoryClick(CatalogFragment.CategoryItem category, int position);
    }

    public CategoryAdapter(List<CatalogFragment.CategoryItem> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CatalogFragment.CategoryItem category = categories.get(position);
        holder.tvCategoryName.setText(category.getName());
        
        if (selectedPosition == holder.getAdapterPosition()) {
            holder.cardCategory.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.artifex_primary));
            holder.tvCategoryName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.artifex_surface));
            holder.cardCategory.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.artifex_primary));
        } else {
            holder.cardCategory.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.artifex_surface));
            holder.tvCategoryName.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.artifex_text_primary));
            holder.cardCategory.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.artifex_border));
        }

        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION && currentPos != selectedPosition) {
                int previousPos = selectedPosition;
                selectedPosition = currentPos;
                notifyItemChanged(previousPos);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onCategoryClick(category, selectedPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories != null ? categories.size() : 0;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardCategory;
        TextView tvCategoryName;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            cardCategory = itemView.findViewById(R.id.card_category);
            tvCategoryName = itemView.findViewById(R.id.tv_category_name);
        }
    }
}