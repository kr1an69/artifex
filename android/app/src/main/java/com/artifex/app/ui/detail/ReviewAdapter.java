package com.artifex.app.ui.detail;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;
import com.artifex.app.data.model.ReviewResponse;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách đánh giá sản phẩm (Đức - Bạn 3)
 * Sử dụng Glide để load ảnh avatar người dùng và ảnh review từ Cloudinary
 */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder> {

    private final Context context;
    private final List<ReviewResponse> reviewList = new ArrayList<>();
    private OnImageClickListener onImageClickListener;

    public interface OnImageClickListener {
        void onImageClick(String imageUrl);
    }

    public ReviewAdapter(Context context) {
        this.context = context;
    }

    public void setOnImageClickListener(OnImageClickListener listener) {
        this.onImageClickListener = listener;
    }

    public void setReviews(List<ReviewResponse> list) {
        this.reviewList.clear();
        if (list != null) {
            this.reviewList.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_review, parent, false);
        return new ReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReviewViewHolder holder, int position) {
        ReviewResponse review = reviewList.get(position);
        if (review == null) return;

        // 1. Tên người dùng
        String userName = !TextUtils.isEmpty(review.getUserName()) ? review.getUserName() : "Người dùng";
        holder.tvUserName.setText(userName);

        // 2. Ngày tháng (format hiển thị đẹp)
        String dateStr = review.getCreatedAt();
        if (!TextUtils.isEmpty(dateStr)) {
            // Chuỗi trả về từ backend thường là "2026-10-28T14:30:00" -> đổi "T" thành khoảng trắng
            holder.tvReviewDate.setText(dateStr.replace("T", " "));
        } else {
            holder.tvReviewDate.setText("");
        }

        // 3. Số sao đánh giá (Rating)
        int rating = review.getRating() != null ? review.getRating() : 5;
        holder.ratingBar.setRating(rating);

        // 4. Nội dung nhận xét
        if (!TextUtils.isEmpty(review.getComment())) {
            holder.tvComment.setVisibility(View.VISIBLE);
            holder.tvComment.setText(review.getComment());
        } else {
            holder.tvComment.setVisibility(View.GONE);
        }

        // 5. Avatar người dùng (qua Glide)
        Glide.with(context)
                .load(review.getUserAvatar())
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .into(holder.imgUserAvatar);

        // 6. Ảnh review từ Cloudinary (nếu có)
        String imageUrl = review.getImageUrl();
        if (!TextUtils.isEmpty(imageUrl)) {
            holder.cardReviewImage.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(imageUrl)
                    .centerCrop()
                    .into(holder.imgReview);

            // Bấm vào ảnh để xem phóng to
            holder.cardReviewImage.setOnClickListener(v -> {
                if (onImageClickListener != null) {
                    onImageClickListener.onImageClick(imageUrl);
                }
            });
        } else {
            holder.cardReviewImage.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return reviewList.size();
    }

    static class ReviewViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView imgUserAvatar;
        TextView tvUserName;
        TextView tvReviewDate;
        RatingBar ratingBar;
        TextView tvComment;
        MaterialCardView cardReviewImage;
        ImageView imgReview;

        public ReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            imgUserAvatar = itemView.findViewById(R.id.imgUserAvatar);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvReviewDate = itemView.findViewById(R.id.tvReviewDate);
            ratingBar = itemView.findViewById(R.id.ratingBar);
            tvComment = itemView.findViewById(R.id.tvComment);
            cardReviewImage = itemView.findViewById(R.id.cardReviewImage);
            imgReview = itemView.findViewById(R.id.imgReview);
        }
    }
}
