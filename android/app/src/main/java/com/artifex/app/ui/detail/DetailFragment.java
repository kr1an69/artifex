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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.artifex.app.R;
import com.artifex.app.api.ApiService;
import com.artifex.app.api.RetrofitClient;
import com.artifex.app.data.local.AppDatabase;
import com.artifex.app.data.local.CartDao;
import com.artifex.app.data.local.CartItem;
import com.artifex.app.data.model.ProductRatingSummary;
import com.artifex.app.data.model.ReviewRequest;
import com.artifex.app.data.model.ReviewResponse;
import com.artifex.app.ui.auth.SharedPrefManager;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
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
 * Tích hợp đầy đủ:
 * 1. Biểu đồ thanh phân bố sao (5 sao -> 1 sao)
 * 2. Bộ lọc đánh giá (Tất cả, Có hình ảnh, 5★ -> 1★)
 * 3. Thông số kỹ thuật linh kiện thực tế + Thêm vào giỏ hàng Room DB + Lấy User từ SharedPrefManager
 */
public class DetailFragment extends Fragment {

    public static final String ARG_PRODUCT_ID = "product_id";

    private long currentProductId = 1L;
    private String currentProductName = "Intel Core i5-13600K";
    private double currentProductPrice = 7890000.0;
    private String currentProductImage = "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=800&q=80";

    private SwipeRefreshLayout swipeRefreshDetail;
    private ChipGroup chipGroupProducts;

    // Views thông tin sản phẩm & thông số
    private ImageView imgProductThumb;
    private TextView tvProductCategory, tvProductStock, tvProductName, tvProductPrice, tvProductDescription;
    private TextView tvSpecCode, tvSpecCategory;
    private MaterialButton btnAddToCartDetail;

    // Views thống kê Rating & Biểu đồ phân bố sao
    private TextView tvAverageRating, tvTotalReviews;
    private RatingBar ratingBarSummary;
    private ProgressBar pbStar5, pbStar4, pbStar3, pbStar2, pbStar1;
    private TextView tvCountStar5, tvCountStar4, tvCountStar3, tvCountStar2, tvCountStar1;
    private MaterialButton btnOpenWriteReview;

    // Views bộ lọc & danh sách đánh giá
    private TextView tvReviewListTitle, tvEmptyReviews;
    private ChipGroup chipGroupReviewFilter;
    private ProgressBar progressBarReviews;
    private RecyclerView rvReviews;

    private ReviewAdapter reviewAdapter;
    private ApiService apiService;
    private final List<ReviewResponse> allReviews = new ArrayList<>();

    // Biến tạm cho Dialog viết đánh giá
    private Uri selectedImageUri = null;
    private ImageView imgDialogPreview;
    private MaterialCardView cardDialogPreview;
    private ProgressBar progressBarDialog;
    private MaterialButton btnDialogSubmit;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    public DetailFragment() {
    }

    public static DetailFragment newInstance(long productId) {
        DetailFragment fragment = new DetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PRODUCT_ID, productId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null && getArguments().containsKey(ARG_PRODUCT_ID)) {
            currentProductId = getArguments().getLong(ARG_PRODUCT_ID, 1L);
        }

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
        setupProductSwitcher();
        setupEvents();

        // Tải toàn bộ thông tin của sản phẩm hiện tại
        refreshAllData();
    }

    private void initViews(View view) {
        swipeRefreshDetail = view.findViewById(R.id.swipeRefreshDetail);
        chipGroupProducts = view.findViewById(R.id.chipGroupProducts);

        imgProductThumb = view.findViewById(R.id.imgProductThumb);
        tvProductCategory = view.findViewById(R.id.tvProductCategory);
        tvProductStock = view.findViewById(R.id.tvProductStock);
        tvProductName = view.findViewById(R.id.tvProductName);
        tvProductPrice = view.findViewById(R.id.tvProductPrice);
        tvProductDescription = view.findViewById(R.id.tvProductDescription);
        tvSpecCode = view.findViewById(R.id.tvSpecCode);
        tvSpecCategory = view.findViewById(R.id.tvSpecCategory);
        btnAddToCartDetail = view.findViewById(R.id.btnAddToCartDetail);

        tvAverageRating = view.findViewById(R.id.tvAverageRating);
        tvTotalReviews = view.findViewById(R.id.tvTotalReviews);
        ratingBarSummary = view.findViewById(R.id.ratingBarSummary);
        pbStar5 = view.findViewById(R.id.pbStar5);
        pbStar4 = view.findViewById(R.id.pbStar4);
        pbStar3 = view.findViewById(R.id.pbStar3);
        pbStar2 = view.findViewById(R.id.pbStar2);
        pbStar1 = view.findViewById(R.id.pbStar1);
        tvCountStar5 = view.findViewById(R.id.tvCountStar5);
        tvCountStar4 = view.findViewById(R.id.tvCountStar4);
        tvCountStar3 = view.findViewById(R.id.tvCountStar3);
        tvCountStar2 = view.findViewById(R.id.tvCountStar2);
        tvCountStar1 = view.findViewById(R.id.tvCountStar1);
        btnOpenWriteReview = view.findViewById(R.id.btnOpenWriteReview);

        tvReviewListTitle = view.findViewById(R.id.tvReviewListTitle);
        chipGroupReviewFilter = view.findViewById(R.id.chipGroupReviewFilter);
        progressBarReviews = view.findViewById(R.id.progressBarReviews);
        tvEmptyReviews = view.findViewById(R.id.tvEmptyReviews);
        rvReviews = view.findViewById(R.id.rvReviews);
    }

    private void setupRecyclerView() {
        reviewAdapter = new ReviewAdapter(requireContext());
        rvReviews.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvReviews.setAdapter(reviewAdapter);

        reviewAdapter.setOnImageClickListener(this::showEnlargedImageDialog);
    }

    /**
     * Thanh chọn nhanh linh kiện (hỗ trợ test chuyển đổi nhiều sản phẩm trong seed_data.sql)
     */
    private void setupProductSwitcher() {
        // Tạo sẵn các chip mặc định khớp với seed_data.sql
        long[] defaultIds = {1L, 2L, 4L, 7L, 9L, 11L};
        String[] defaultNames = {
                "#1 Core i5-13600K",
                "#2 Ryzen 7 7800X3D",
                "#4 RTX 4070 Super",
                "#7 RAM Corsair DDR5",
                "#9 SSD Samsung 990 Pro",
                "#11 ROG Strix B760-F"
        };

        populateProductChips(defaultIds, defaultNames);

        // Đồng thời gọi API lấy danh sách sản phẩm thật từ Backend nếu server đang chạy
        apiService.getProducts(14).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                if (!isAdded() || !response.isSuccessful() || response.body() == null) return;
                try {
                    Object dataObj = response.body().get("data");
                    if (dataObj instanceof Map) {
                        Object contentObj = ((Map<?, ?>) dataObj).get("content");
                        if (contentObj instanceof List) {
                            List<?> rawList = (List<?>) contentObj;
                            if (!rawList.isEmpty()) {
                                long[] ids = new long[rawList.size()];
                                String[] names = new String[rawList.size()];
                                for (int i = 0; i < rawList.size(); i++) {
                                    Object item = rawList.get(i);
                                    if (item instanceof Map) {
                                        Map<?, ?> map = (Map<?, ?>) item;
                                        ids[i] = map.get("id") instanceof Number ? ((Number) map.get("id")).longValue() : (i + 1);
                                        String n = map.get("name") != null ? map.get("name").toString() : ("SP #" + ids[i]);
                                        if (n.length() > 22) n = n.substring(0, 22) + "...";
                                        names[i] = "#" + ids[i] + " " + n;
                                    }
                                }
                                populateProductChips(ids, names);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
            }
        });
    }

    private void populateProductChips(long[] ids, String[] names) {
        if (!isAdded() || chipGroupProducts == null) return;
        chipGroupProducts.removeAllViews();

        for (int i = 0; i < ids.length; i++) {
            final long prodId = ids[i];
            Chip chip = new Chip(requireContext());
            chip.setText(names[i]);
            chip.setCheckable(true);
            chip.setClickable(true);
            if (prodId == currentProductId) {
                chip.setChecked(true);
            }
            chip.setOnClickListener(v -> {
                if (currentProductId != prodId) {
                    currentProductId = prodId;
                    refreshAllData();
                }
            });
            chipGroupProducts.addView(chip);
        }
    }

    private void setupEvents() {
        swipeRefreshDetail.setOnRefreshListener(this::refreshAllData);

        btnOpenWriteReview.setOnClickListener(v -> showWriteReviewDialog());

        btnAddToCartDetail.setOnClickListener(v -> addCurrentProductToCart());

        chipGroupReviewFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyReviewFilter());
    }

    private void refreshAllData() {
        loadProductDetail();
        loadRatingSummary();
        loadReviews();
    }

    /**
     * Tải chi tiết linh kiện từ API GET /api/products/{id} (Của bạn Đạt)
     */
    private void loadProductDetail() {
        // Cập nhật mã linh kiện trước
        tvSpecCode.setText(String.format(Locale.getDefault(), "ART-PC-%03d", currentProductId));

        apiService.getProductById(currentProductId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        Object dataObj = response.body().get("data");
                        if (dataObj instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) dataObj;

                            String name = map.get("name") != null ? map.get("name").toString() : "Linh kiện PC";
                            double price = map.get("price") instanceof Number ? ((Number) map.get("price")).doubleValue() : 0.0;
                            int stock = map.get("stock") instanceof Number ? ((Number) map.get("stock")).intValue() : 10;
                            String desc = map.get("description") != null ? map.get("description").toString() : "";
                            String imageUrl = map.get("imageUrl") != null ? map.get("imageUrl").toString() : "";
                            String categoryName = map.get("categoryName") != null ? map.get("categoryName").toString() : "LINH KIỆN";

                            currentProductName = name;
                            currentProductPrice = price;
                            currentProductImage = imageUrl;

                            tvProductName.setText(name);
                            tvProductPrice.setText(formatVndPrice(price));
                            tvProductDescription.setText(!TextUtils.isEmpty(desc) ? desc : "Linh kiện chính hãng phân phối tại Artifex PC Gear.");
                            tvProductCategory.setText(categoryName.toUpperCase());
                            tvSpecCategory.setText("Danh mục " + categoryName + " chính hãng");

                            if (stock > 0) {
                                tvProductStock.setText(String.format(Locale.getDefault(), "✓ Còn hàng (%d)", stock));
                                tvProductStock.setTextColor(ContextCompat.getColor(requireContext(), R.color.artifex_success));
                                btnAddToCartDetail.setEnabled(true);
                            } else {
                                tvProductStock.setText("✕ Tạm hết hàng");
                                tvProductStock.setTextColor(ContextCompat.getColor(requireContext(), R.color.artifex_error));
                                btnAddToCartDetail.setEnabled(false);
                            }

                            if (!TextUtils.isEmpty(imageUrl)) {
                                Glide.with(requireContext())
                                        .load(imageUrl)
                                        .placeholder(R.drawable.ic_nav_detail)
                                        .error(R.drawable.ic_nav_detail)
                                        .centerCrop()
                                        .into(imgProductThumb);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                // Giữ thông tin mặc định trên UI nếu server chưa bật
            }
        });
    }

    /**
     * Thêm sản phẩm hiện tại vào Giỏ hàng (Room Database của bạn Hiếu)
     */
    private void addCurrentProductToCart() {
        new Thread(() -> {
            try {
                CartDao cartDao = AppDatabase.getInstance(requireContext()).cartDao();
                List<CartItem> currentItems = cartDao.getAllCartItems();
                CartItem existing = null;
                if (currentItems != null) {
                    for (CartItem item : currentItems) {
                        if (item.getProductId() == (int) currentProductId) {
                            existing = item;
                            break;
                        }
                    }
                }

                if (existing != null) {
                    existing.setQuantity(existing.getQuantity() + 1);
                    cartDao.update(existing);
                } else {
                    CartItem newItem = new CartItem(
                            (int) currentProductId,
                            currentProductName,
                            currentProductPrice,
                            1,
                            currentProductImage
                    );
                    cartDao.insert(newItem);
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "🛍️ Đã thêm " + currentProductName + " vào giỏ hàng!", Toast.LENGTH_SHORT).show()
                    );
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Không thể thêm vào giỏ: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            }
        }).start();
    }

    /**
     * Tải tóm tắt Rating & cập nhật biểu đồ thanh 5 sao -> 1 sao
     */
    private void loadRatingSummary() {
        apiService.getProductRatingSummary(currentProductId).enqueue(new Callback<ProductRatingSummary>() {
            @Override
            public void onResponse(@NonNull Call<ProductRatingSummary> call, @NonNull Response<ProductRatingSummary> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    ProductRatingSummary summary = response.body();
                    double avg = summary.getAverageRating() != null ? summary.getAverageRating() : 0.0;
                    int total = summary.getTotalReviews() != null ? summary.getTotalReviews() : 0;

                    tvAverageRating.setText(String.format(Locale.getDefault(), "%.1f", avg));
                    ratingBarSummary.setRating((float) avg);
                    tvTotalReviews.setText(String.format(Locale.getDefault(), "(%d đánh giá)", total));

                    Map<Integer, Long> starCounts = summary.getStarCounts();
                    long c5 = getStarCount(starCounts, 5);
                    long c4 = getStarCount(starCounts, 4);
                    long c3 = getStarCount(starCounts, 3);
                    long c2 = getStarCount(starCounts, 2);
                    long c1 = getStarCount(starCounts, 1);

                    updateStarDistributionBars(total, c5, c4, c3, c2, c1);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProductRatingSummary> call, @NonNull Throwable t) {
            }
        });
    }

    private long getStarCount(Map<Integer, Long> map, int star) {
        if (map == null) return 0L;
        Long val = map.get(star);
        return val != null ? val : 0L;
    }

    private void updateStarDistributionBars(int total, long c5, long c4, long c3, long c2, long c1) {
        tvCountStar5.setText(String.valueOf(c5));
        tvCountStar4.setText(String.valueOf(c4));
        tvCountStar3.setText(String.valueOf(c3));
        tvCountStar2.setText(String.valueOf(c2));
        tvCountStar1.setText(String.valueOf(c1));

        if (total <= 0) {
            pbStar5.setProgress(0);
            pbStar4.setProgress(0);
            pbStar3.setProgress(0);
            pbStar2.setProgress(0);
            pbStar1.setProgress(0);
        } else {
            pbStar5.setProgress((int) ((c5 * 100) / total));
            pbStar4.setProgress((int) ((c4 * 100) / total));
            pbStar3.setProgress((int) ((c3 * 100) / total));
            pbStar2.setProgress((int) ((c2 * 100) / total));
            pbStar1.setProgress((int) ((c1 * 100) / total));
        }
    }

    /**
     * Tải danh sách đánh giá từ API
     */
    private void loadReviews() {
        progressBarReviews.setVisibility(View.VISIBLE);
        apiService.getReviewsByProduct(currentProductId).enqueue(new Callback<List<ReviewResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<ReviewResponse>> call, @NonNull Response<List<ReviewResponse>> response) {
                if (!isAdded()) return;
                progressBarReviews.setVisibility(View.GONE);
                swipeRefreshDetail.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    allReviews.clear();
                    allReviews.addAll(response.body());
                    applyReviewFilter();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ReviewResponse>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                progressBarReviews.setVisibility(View.GONE);
                swipeRefreshDetail.setRefreshing(false);
                Toast.makeText(requireContext(), "Không thể tải đánh giá: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Lọc danh sách đánh giá theo Chip đang chọn (Tất cả, Có hình ảnh, 5★ -> 1★)
     */
    private void applyReviewFilter() {
        if (!isAdded()) return;
        int checkedId = chipGroupReviewFilter.getCheckedChipId();
        List<ReviewResponse> filtered = new ArrayList<>();

        for (ReviewResponse r : allReviews) {
            int star = r.getRating() != null ? r.getRating() : 5;
            boolean hasImg = !TextUtils.isEmpty(r.getImageUrl());

            if (checkedId == R.id.chipFilterWithImage) {
                if (hasImg) filtered.add(r);
            } else if (checkedId == R.id.chipFilter5Star) {
                if (star == 5) filtered.add(r);
            } else if (checkedId == R.id.chipFilter4Star) {
                if (star == 4) filtered.add(r);
            } else if (checkedId == R.id.chipFilter3Star) {
                if (star == 3) filtered.add(r);
            } else if (checkedId == R.id.chipFilter2Star) {
                if (star == 2) filtered.add(r);
            } else if (checkedId == R.id.chipFilter1Star) {
                if (star == 1) filtered.add(r);
            } else {
                filtered.add(r);
            }
        }

        reviewAdapter.setReviews(filtered);
        tvReviewListTitle.setText(String.format(Locale.getDefault(), "Nhận xét từ khách hàng (%d)", filtered.size()));
        tvEmptyReviews.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * Hiển thị Dialog viết đánh giá sản phẩm (Kết nối userId từ SharedPrefManager của bạn Khoa)
     */
    private void showWriteReviewDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_write_review, null);

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
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
        if (tvDialogTitle != null) {
            tvDialogTitle.setText("Đánh giá: " + currentProductName);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

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

        btnSelectImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        btnRemoveImage.setOnClickListener(v -> {
            selectedImageUri = null;
            cardDialogPreview.setVisibility(View.GONE);
        });

        btnCancelReview.setOnClickListener(v -> dialog.dismiss());

        btnDialogSubmit.setOnClickListener(v -> {
            int rating = Math.round(ratingBarInput.getRating());
            String comment = etComment.getText() != null ? etComment.getText().toString().trim() : "";

            if (TextUtils.isEmpty(comment)) {
                etComment.setError("Vui lòng nhập nhận xét của bạn");
                etComment.requestFocus();
                return;
            }

            if (selectedImageUri != null) {
                uploadImageAndCreateReview(dialog, rating, comment, selectedImageUri);
            } else {
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
            byte[] buffer = new byte[1024];
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
     * Gửi Review qua API Backend (Lấy userId của người đang đăng nhập từ SharedPrefManager)
     */
    private void submitReview(AlertDialog dialog, int rating, String comment, @Nullable String imageUrl) {
        setDialogLoading(true);

        long activeUserId = SharedPrefManager.getInstance(requireContext()).getUserId();
        ReviewRequest request = new ReviewRequest(currentProductId, activeUserId, rating, comment, imageUrl);

        apiService.createReview(request).enqueue(new Callback<ReviewResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReviewResponse> call, @NonNull Response<ReviewResponse> response) {
                setDialogLoading(false);
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), "🎉 Gửi đánh giá thành công!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
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

    private String formatVndPrice(double price) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,### đ", symbols);
        return formatter.format(price);
    }
}
