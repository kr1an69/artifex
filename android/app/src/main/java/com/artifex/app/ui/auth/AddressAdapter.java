package com.artifex.app.ui.auth;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.AddressViewHolder> {

    public interface OnAddressListener {
        void onSelect(UserAddress address);
        void onDelete(UserAddress address);
    }

    private final List<UserAddress> addresses;
    private final OnAddressListener listener;

    public AddressAdapter(List<UserAddress> addresses, OnAddressListener listener) {
        this.addresses = addresses;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AddressViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_address_card, parent, false);
        return new AddressViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AddressViewHolder holder, int position) {
        UserAddress item = addresses.get(position);
        holder.tvReceiverName.setText(item.getReceiverName());
        holder.tvPhone.setText(item.getPhone());
        holder.tvAddressLine.setText(item.getAddressLine());
        holder.tvTag.setText(item.getTag());

        if (item.isDefault()) {
            holder.tvDefaultBadge.setVisibility(View.VISIBLE);
            holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.artifex_primary));
            holder.card.setStrokeWidth(4);
        } else {
            holder.tvDefaultBadge.setVisibility(View.GONE);
            holder.card.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.artifex_border));
            holder.card.setStrokeWidth(2);
        }

        holder.card.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelect(item);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return addresses != null ? addresses.size() : 0;
    }

    static class AddressViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvReceiverName, tvPhone, tvAddressLine, tvTag, tvDefaultBadge;
        ImageButton btnDelete;

        AddressViewHolder(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.cardAddressItem);
            tvReceiverName = v.findViewById(R.id.tvItemReceiverName);
            tvPhone = v.findViewById(R.id.tvItemPhone);
            tvAddressLine = v.findViewById(R.id.tvItemAddressLine);
            tvTag = v.findViewById(R.id.tvItemTag);
            tvDefaultBadge = v.findViewById(R.id.tvItemDefaultBadge);
            btnDelete = v.findViewById(R.id.btnItemDelete);
        }
    }
}
