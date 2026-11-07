package com.artifex.app.ui.detail;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.artifex.app.R;
import com.artifex.app.api.ApiService;
import com.artifex.app.api.RetrofitClient;
import com.artifex.app.data.model.ProductRatingSummary;
import com.artifex.app.data.model.ReviewRequest;
import com.artifex.app.data.model.ReviewResponse;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Fragment Chi tiết linh kiện & Đánh giá (Đức - Bạn 3)
 * Tuân thủ Loose Coupling: Mock cứng productId = 1 và userId = 1 để test độc lập.
 */
public class DetailFragment extends Fragment {

    // Mock ID để test độc lập theo CONTEXT.md
    private static final long MOCK_PRODUCT_ID = 1L;
    private static final long MOCK_USER_ID = 1L;

    private SwipeRefreshLayout swipeRefreshDetail;
    private TextView tvAverageRating, tvTotalReviews, tvEmptyReviews;
    private RatingBar ratingBarSummary;
    private MaterialButton btnOpenWriteReview;
    private ProgressBar progressBarReviews;
    private RecyclerView rvReviews;

    private ReviewAdapter reviewAdapter;
    private ApiService apiService;

    // Biến tạm cho Dialog viết đánh giá
    private Uri selectedImageUri = null;
    private ImageView imgDialogPreview;
    private MaterialCardView cardDialogPreview;
    private ProgressBar progressBarDialog;
    private MaterialButton btnDialogSubmit;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    public DetailFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Khởi tạo ActivityResultLauncher để chọn ảnh từ bộ sưu tập máy
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null && cardDialogPreview != null && imgDialogPreview != null) {
                            cardDialogPreview.setVisibility(View.VISIBLE);
                            Glide.with(requireContext()).load(selectedImageUri).into(imgDialogPreview);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        apiService = RetrofitClient.getApiService();

        initViews(view);
        setupRecyclerView();
        setupEvents();

        // Tải dữ liệu ban đầu
        loadRatingSummary();
        loadReviews();
    }

    private void initViews(View view) {
        swipeRefreshDetail = view.findViewById(R.id.swipeRefreshDetail);
        tvAverageRating = view.findViewById(R.id.tvAverageRating);
        tvTotalReviews = view.findViewById(R.id.tvTotalReviews);
        ratingBarSummary = view.findViewById(R.id.ratingBarSummary);
        btnOpenWriteReview = view.findViewById(R.id.btnOpenWriteReview);
        progressBarReviews = view.findViewById(R.id.progressBarReviews);
        tvEmptyReviews = view.findViewById(R.id.tvEmptyReviews);
        rvReviews = view.findViewById(R.id.rvReviews);
    }

    private void setupRecyclerView() {
        reviewAdapter = new ReviewAdapter(requireContext());
        rvReviews.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReviews.setAdapter(reviewAdapter);

        reviewAdapter.setOnImageClickListener(imageUrl -> {
            // Xem trước ảnh phóng to đơn giản
            showEnlargedImageDialog(imageUrl);
        });
    }

    private void setupEvents() {
        swipeRefreshDetail.setOnRefreshListener(() -> {
            loadRatingSummary();
            loadReviews();
        });

        btnOpenWriteReview.setOnClickListener(v -> showWriteReviewDialog());
    }

    /**
     * Tải tóm tắt Rating (điểm trung bình, tổng số đánh giá)
     */
    private void loadRatingSummary() {
        apiService.getProductRatingSummary(MOCK_PRODUCT_ID).enqueue(new Callback<ProductRatingSummary>() {
            @Override
            public void onResponse(@NonNull Call<ProductRatingSummary> call, @NonNull Response<ProductRatingSummary> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ProductRatingSummary summary = response.body();
                    double avg = summary.getAverageRating() != null ? summary.getAverageRating() : 0.0;
                    int total = summary.getTotalReviews() != null ? summary.getTotalReviews() : 0;

                    tvAverageRating.setText(String.format(Locale.getDefault(), "%.1f", avg));
                    ratingBarSummary.setRating((float) avg);
                    tvTotalReviews.setText(String.format(Locale.getDefault(), "(%d đánh giá)", total));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProductRatingSummary> call, @NonNull Throwable t) {
                // Giữ giá trị mặc định nếu mất kết nối
            }
        });
    }

    /**
     * Tải danh sách đánh giá từ API
     */
    private void loadReviews() {
        progressBarReviews.setVisibility(View.VISIBLE);
        apiService.getReviewsByProduct(MOCK_PRODUCT_ID).enqueue(new Callback<List<ReviewResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<ReviewResponse>> call, @NonNull Response<List<ReviewResponse>> response) {
                if (isAdded()) {
                    progressBarReviews.setVisibility(View.GONE);
                    swipeRefreshDetail.setRefreshing(false);
                    if (response.isSuccessful() && response.body() != null) {
                        List<ReviewResponse> list = response.body();
                        reviewAdapter.setReviews(list);
                        tvEmptyReviews.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ReviewResponse>> call, @NonNull Throwable t) {
                if (isAdded()) {
                    progressBarReviews.setVisibility(View.GONE);
                    swipeRefreshDetail.setRefreshing(false);
                    Toast.makeText(requireContext(), "Không thể tải đánh giá: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Hiển thị Dialog viết đánh giá sản phẩm
     */
    private void showWriteReviewDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_write_review, null);

        RatingBar ratingBarInput = dialogView.findViewById(R.id.ratingBarInput);
        TextView tvRatingHint = dialogView.findViewById(R.id.tvRatingHint);
        TextInputEditText etComment = dialogView.findViewById(R.id.etComment);
        MaterialButton btnSelectImage = dialogView.findViewById(R.id.btnSelectImage);
        cardDialogPreview = dialogView.findViewById(R.id.cardImagePreview);
        imgDialogPreview = dialogView.findViewById(R.id.imgPreview);
        ImageView btnRemoveImage = dialogView.findViewById(R.id.btnRemoveImage);
        progressBarDialog = dialogView.findViewById(R.id.progressBarReview);
        MaterialButton btnCancelReview = dialogView.findViewById(R.id.btnCancelReview);
        btnDialogSubmit = dialogView.findViewById(R.id.btnSubmitReview);

        selectedImageUri = null;

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // Bắt sự kiện đổi rating sao
        ratingBarInput.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            int r = Math.round(rating);
            if (r < 1) {
                ratingBar.setRating(1);
                r = 1;
            }
            switch (r) {
                case 1: tvRatingHint.setText("Rất tệ (1 sao)"); break;
                case 2: tvRatingHint.setText("Không hài lòng (2 sao)"); break;
                case 3: tvRatingHint.setText("Bình thường (3 sao)"); break;
                case 4: tvRatingHint.setText("Hài lòng (4 sao)"); break;
                case 5: tvRatingHint.setText("Tuyệt vời (5 sao)"); break;
            }
        });

        // Bấm chọn ảnh
        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        // Xóa ảnh đã chọn
        btnRemoveImage.setOnClickListener(v -> {
            selectedImageUri = null;
            cardDialogPreview.setVisibility(View.GONE);
        });

        btnCancelReview.setOnClickListener(v -> dialog.dismiss());

        // Bấm gửi đánh giá
        btnDialogSubmit.setOnClickListener(v -> {
            int rating = Math.round(ratingBarInput.getRating());
            String comment = etComment.getText() != null ? etComment.getText().toString().trim() : "";

            if (selectedImageUri != null) {
                // Bước 1: Upload ảnh lên Cloudinary trước
                uploadImageAndCreateReview(dialog, rating, comment, selectedImageUri);
            } else {
                // Gửi review không có ảnh
                submitReview(dialog, rating, comment, null);
            }
        });

        dialog.show();
    }

    /**
     * Upload ảnh lên Cloudinary rồi gọi API tạo Review
     */
    private void uploadImageAndCreateReview(AlertDialog dialog, int rating, String comment, Uri imageUri) {
        setDialogLoading(true);

        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                setDialogLoading(false);
                Toast.makeText(requireContext(), "Không thể đọc file ảnh đã chọn", Toast.LENGTH_SHORT).show();
                return;
            }

            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byte[] bytes = byteBuffer.toByteArray();

            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), bytes);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", "review_" + System.currentTimeMillis() + ".jpg", requestFile);

            apiService.uploadReviewImage(body).enqueue(new Callback<Map<String, String>>() {
                @Override
                public void onResponse(@NonNull Call<Map<String, String>> call, @NonNull Response<Map<String, String>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().containsKey("imageUrl")) {
                        String imageUrl = response.body().get("imageUrl");
                        submitReview(dialog, rating, comment, imageUrl);
                    } else {
                        setDialogLoading(false);
                        Toast.makeText(requireContext(), "Upload ảnh lên Cloudinary thất bại!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Map<String, String>> call, @NonNull Throwable t) {
                    setDialogLoading(false);
                    Toast.makeText(requireContext(), "Lỗi kết nối upload: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            setDialogLoading(false);
            Toast.makeText(requireContext(), "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Gửi Review qua API Backend
     */
    private void submitReview(AlertDialog dialog, int rating, String comment, @Nullable String imageUrl) {
        setDialogLoading(true);

        ReviewRequest request = new ReviewRequest(MOCK_PRODUCT_ID, MOCK_USER_ID, rating, comment, imageUrl);

        apiService.createReview(request).enqueue(new Callback<ReviewResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReviewResponse> call, @NonNull Response<ReviewResponse> response) {
                setDialogLoading(false);
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), "Gửi đánh giá thành công!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    // Tự động reload danh sách và rating trung bình
                    loadRatingSummary();
                    loadReviews();
                } else {
                    Toast.makeText(requireContext(), "Không thể gửi đánh giá (Mã lỗi: " + response.code() + ")", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ReviewResponse> call, @NonNull Throwable t) {
                setDialogLoading(false);
                Toast.makeText(requireContext(), "Lỗi mạng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setDialogLoading(boolean loading) {
        if (progressBarDialog != null) progressBarDialog.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (btnDialogSubmit != null) btnDialogSubmit.setEnabled(!loading);
    }

    private void showEnlargedImageDialog(String imageUrl) {
        ImageView iv = new ImageView(requireContext());
        iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        iv.setAdjustViewBounds(true);
        Glide.with(requireContext()).load(imageUrl).into(iv);

        new MaterialAlertDialogBuilder(requireContext())
                .setView(iv)
                .setPositiveButton("Đóng", null)
                .show();
    }
}
