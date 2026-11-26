package com.artifex.app.ui.catalog;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;
import com.artifex.app.api.RetrofitClient;
import com.artifex.app.ui.auth.SharedPrefManager;
import com.artifex.app.ui.catalog.model.CatalogApiResponse;
import com.artifex.app.ui.catalog.model.CategoryResponse;
import com.artifex.app.ui.catalog.model.PageResponse;
import com.artifex.app.ui.catalog.model.ProductResponse;
import com.artifex.app.ui.detail.DetailFragment;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CatalogFragment extends Fragment {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024; // 10MB

    // --- UI Views ---
    private RecyclerView rvCategories;
    private RecyclerView rvProducts;
    private EditText editSearch;
    private ImageButton btnClearSearch;
    private ImageButton btnFilter;
    private ImageButton btnRefreshCatalog;
    private MaterialCardView btnAddProductCloudinary;
    private View viewFilterDot;
    private TextView tvResultCount;
    private TextView tvActiveFilterInfo;
    private TextView btnQuickResetFilter;
    private LinearLayout layoutLoading;
    private LinearLayout layoutEmptyState;
    private MaterialButton btnEmptyReset;
    private ProgressBar progressLoadMore;

    // --- Adapters & Data ---
    private CategoryAdapter categoryAdapter;
    private ProductAdapter productAdapter;
    private List<CategoryItem> categoryList;
    private List<ProductItem> allProducts;
    private List<ProductItem> displayedProducts;

    // --- Filter & Sort State ---
    private String selectedCategoryId = "cat_all";
    private String currentSearchKeyword = "";
    private int currentSortOption = 0; // 0: Default, 1: Price ASC, 2: Price DESC, 3: Rating DESC
    private int currentPriceRange = 0; // 0: All, 1: <5M, 2: 5M-15M, 3: 15M-30M, 4: >30M
    private boolean filterInStockOnly = false;
    private boolean filterDiscountOnly = false;

    // --- Pagination & Network State ---
    private int currentPage = 0;
    private boolean isLoadingMore = false;
    private boolean isLastPage = false;
    private boolean isOfflineFallbackMode = false;
    private int productsRequestSeq = 0;
    private Call<CatalogApiResponse<List<CategoryResponse>>> activeCategoriesCall;
    private Call<CatalogApiResponse<PageResponse<ProductResponse>>> activeProductsCall;

    // --- Debounce Search & Background IO ---
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private ExecutorService ioExecutor;

    // --- Cloudinary Image Picker State ---
    private ActivityResultLauncher<String> imagePickerLauncher;
    private Uri selectedImageUri = null;
    private ImageView activeDialogPreviewImage = null;
    private View activeDialogPlaceholder = null;
    private TextView activeDialogStatusText = null;

    public CatalogFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    selectedImageUri = uri;
                    if (!isAdded() || getContext() == null) {
                        return;
                    }
                    if (activeDialogPreviewImage != null) {
                        Glide.with(this)
                                .load(uri)
                                .centerCrop()
                                .into(activeDialogPreviewImage);
                    }
                    if (activeDialogPlaceholder != null) {
                        activeDialogPlaceholder.setVisibility(View.GONE);
                    }
                    if (activeDialogStatusText != null) {
                        activeDialogStatusText.setText("Đã chọn ảnh, sẵn sàng tải lên Cloudinary");
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_catalog, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ioExecutor = Executors.newSingleThreadExecutor();

        // 1. Ánh xạ View
        rvCategories = view.findViewById(R.id.rv_categories);
        rvProducts = view.findViewById(R.id.rv_products);
        editSearch = view.findViewById(R.id.edit_search);
        btnClearSearch = view.findViewById(R.id.btn_clear_search);
        btnFilter = view.findViewById(R.id.btn_filter);
        btnRefreshCatalog = view.findViewById(R.id.btn_refresh_catalog);
        btnAddProductCloudinary = view.findViewById(R.id.btn_add_product_cloudinary);
        viewFilterDot = view.findViewById(R.id.view_filter_dot);
        tvResultCount = view.findViewById(R.id.tv_result_count);
        tvActiveFilterInfo = view.findViewById(R.id.tv_active_filter_info);
        btnQuickResetFilter = view.findViewById(R.id.btn_quick_reset_filter);
        layoutLoading = view.findViewById(R.id.layout_loading);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        btnEmptyReset = view.findViewById(R.id.btn_empty_reset);
        progressLoadMore = view.findViewById(R.id.progress_load_more);

        // 2. Khởi tạo danh sách & Adapter
        categoryList = new ArrayList<>();
        allProducts = new ArrayList<>();
        displayedProducts = new ArrayList<>();

        setupFallbackCategories();
        setupFallbackProducts();

        categoryAdapter = new CategoryAdapter(categoryList, (category, position) -> {
            selectedCategoryId = category.getId();
            loadProductsFromApi(0, true);
        });
        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), 2);
        productAdapter = new ProductAdapter(new ArrayList<>(displayedProducts), this::navigateToProductDetail);
        rvProducts.setLayoutManager(gridLayoutManager);
        rvProducts.setAdapter(productAdapter);
        setupPaginationScrollListener(gridLayoutManager);

        // 3. Setup sự kiện tìm kiếm, bộ lọc & phân quyền
        setupSearchEvents();
        updateAdminControlsVisibility();

        btnFilter.setOnClickListener(v -> showFilterBottomSheet());
        btnQuickResetFilter.setOnClickListener(v -> resetAllFilters());
        btnEmptyReset.setOnClickListener(v -> resetAllFilters());
        btnRefreshCatalog.setOnClickListener(v -> refreshCatalogData());
        btnAddProductCloudinary.setOnClickListener(v -> showAddProductCloudinaryDialog());

        // 4. Tải dữ liệu thật từ Backend qua Retrofit API
        fetchCategoriesFromApi();
        loadProductsFromApi(0, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateAdminControlsVisibility();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
        if (activeCategoriesCall != null) {
            activeCategoriesCall.cancel();
            activeCategoriesCall = null;
        }
        if (activeProductsCall != null) {
            activeProductsCall.cancel();
            activeProductsCall = null;
        }
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
            ioExecutor = null;
        }
        activeDialogPreviewImage = null;
        activeDialogPlaceholder = null;
        activeDialogStatusText = null;
    }

    private void updateAdminControlsVisibility() {
        if (btnAddProductCloudinary != null) {
            btnAddProductCloudinary.setVisibility(isAdminUser() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isAdminUser() {
        Context context = getContext();
        if (context == null) return false;
        SharedPrefManager pref = SharedPrefManager.getInstance(context);
        if (!pref.isLoggedIn()) {
            return false;
        }
        return CatalogHelper.isAdminToken(pref.getAuthToken(), System.currentTimeMillis());
    }

    private void setupPaginationScrollListener(GridLayoutManager layoutManager) {
        rvProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || isOfflineFallbackMode || isLoadingMore || isLastPage) {
                    return;
                }
                int visibleCount = layoutManager.getChildCount();
                int totalCount = layoutManager.getItemCount();
                int firstVisible = layoutManager.findFirstVisibleItemPosition();
                if (firstVisible >= 0 && (visibleCount + firstVisible) >= totalCount - 2) {
                    loadProductsFromApi(currentPage + 1, false);
                }
            }
        });
    }

    private void setupSearchEvents() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String nextKeyword = s != null ? s.toString().trim() : "";
                if (nextKeyword.equals(currentSearchKeyword)) {
                    return;
                }
                currentSearchKeyword = nextKeyword;
                btnClearSearch.setVisibility(currentSearchKeyword.isEmpty() ? View.GONE : View.VISIBLE);

                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                searchRunnable = () -> {
                    if (!isAdded() || getView() == null) return;
                    loadProductsFromApi(0, true);
                };
                searchHandler.postDelayed(searchRunnable, 350);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
                loadProductsFromApi(0, true);
                return true;
            }
            return false;
        });

        btnClearSearch.setOnClickListener(v -> {
            editSearch.setText("");
            editSearch.clearFocus();
        });
    }

    // =========================================================================
    // RETROFIT API: TẢI DANH MỤC TỪ SPRING BOOT BACKEND
    // =========================================================================
    private void fetchCategoriesFromApi() {
        if (activeCategoriesCall != null) {
            activeCategoriesCall.cancel();
        }
        activeCategoriesCall = RetrofitClient.getApiService().getCategories();
        activeCategoriesCall.enqueue(new Callback<CatalogApiResponse<List<CategoryResponse>>>() {
            @Override
            public void onResponse(@NonNull Call<CatalogApiResponse<List<CategoryResponse>>> call,
                                   @NonNull Response<CatalogApiResponse<List<CategoryResponse>>> response) {
                if (!isAdded() || getView() == null) return;

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<CategoryResponse> apiCats = response.body().getData();
                    if (!apiCats.isEmpty()) {
                        List<CategoryItem> updatedCategories = new ArrayList<>();
                        updatedCategories.add(new CategoryItem("cat_all", "Tất cả"));
                        for (CategoryResponse cat : apiCats) {
                            updatedCategories.add(new CategoryItem(String.valueOf(cat.getId()), cat.getName()));
                        }

                        // Đồng bộ lại selectedPosition theo selectedCategoryId hiện tại
                        int matchedPosition = 0;
                        boolean found = false;
                        for (int i = 0; i < updatedCategories.size(); i++) {
                            if (updatedCategories.get(i).getId().equals(selectedCategoryId)) {
                                matchedPosition = i;
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            selectedCategoryId = "cat_all";
                            matchedPosition = 0;
                        }

                        categoryList = updatedCategories;
                        categoryAdapter.updateCategories(categoryList, matchedPosition);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<CatalogApiResponse<List<CategoryResponse>>> call, @NonNull Throwable t) {
                // Giữ nguyên danh mục fallback nếu backend chưa bật
            }
        });
    }

    // =========================================================================
    // RETROFIT API: TẢI SẢN PHẨM TỪ SPRING BOOT BACKEND (HỖ TRỢ PHÂN TRANG)
    // =========================================================================
    private void loadProductsFromApi(int page, boolean resetList) {
        if (!isAdded() || getView() == null) return;

        final int requestSeq = ++productsRequestSeq;
        if (activeProductsCall != null) {
            activeProductsCall.cancel();
        }

        if (resetList) {
            currentPage = 0;
            isLastPage = false;
            isLoadingMore = false;
            showLoading(true);
            setLoadMoreVisible(false);
        } else {
            isLoadingMore = true;
            setLoadMoreVisible(true);
        }

        Long categoryIdParam = null;
        if (!"cat_all".equals(selectedCategoryId)) {
            try {
                categoryIdParam = Long.parseLong(selectedCategoryId.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {}
        }

        String sortByParam = "createdAt";
        String directionParam = "DESC";
        if (currentSortOption == 1) {
            sortByParam = "price";
            directionParam = "ASC";
        } else if (currentSortOption == 2) {
            sortByParam = "price";
            directionParam = "DESC";
        } else if (currentSortOption == 3) {
            sortByParam = "ratingAvg";
            directionParam = "DESC";
        }

        String keywordParam = currentSearchKeyword.isEmpty() ? null : currentSearchKeyword;

        activeProductsCall = RetrofitClient.getApiService().getProducts(
                page, PAGE_SIZE, sortByParam, directionParam, categoryIdParam, keywordParam
        );
        activeProductsCall.enqueue(new Callback<CatalogApiResponse<PageResponse<ProductResponse>>>() {
            @Override
            public void onResponse(@NonNull Call<CatalogApiResponse<PageResponse<ProductResponse>>> call,
                                   @NonNull Response<CatalogApiResponse<PageResponse<ProductResponse>>> response) {
                if (!isAdded() || getView() == null || requestSeq != productsRequestSeq) return;
                showLoading(false);
                isLoadingMore = false;
                setLoadMoreVisible(false);

                if (response.isSuccessful() && response.body() != null
                        && response.body().getData() != null
                        && response.body().getData().getContent() != null) {

                    isOfflineFallbackMode = false;
                    PageResponse<ProductResponse> pageData = response.body().getData();
                    List<ProductResponse> apiProducts = pageData.getContent();
                    List<ProductItem> converted = mapResponsesToItems(apiProducts);

                    currentPage = pageData.getNumber();
                    int totalPages = pageData.getTotalPages();
                    isLastPage = (currentPage + 1 >= totalPages) || apiProducts.isEmpty();

                    if (resetList) {
                        allProducts = converted;
                    } else {
                        appendUniqueProducts(converted);
                    }

                    filterAndRenderProducts();

                    // Nếu lọc khoảng giá/tồn kho client khiến trang hiện tại chưa có mục nào nhưng vẫn còn trang kế
                    if (!isLastPage && displayedProducts.isEmpty() && !allProducts.isEmpty()) {
                        loadProductsFromApi(currentPage + 1, false);
                    }
                } else if (resetList) {
                    isLastPage = true;
                    filterAndRenderProducts();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CatalogApiResponse<PageResponse<ProductResponse>>> call, @NonNull Throwable t) {
                if (call.isCanceled() || !isAdded() || getView() == null || requestSeq != productsRequestSeq) return;
                showLoading(false);
                isLoadingMore = false;
                setLoadMoreVisible(false);

                if (resetList) {
                    boolean wasOffline = isOfflineFallbackMode;
                    isOfflineFallbackMode = true;
                    isLastPage = true;
                    if (!wasOffline || allProducts.isEmpty()) {
                        setupFallbackProducts();
                    }
                    filterAndRenderProducts();
                }
            }
        });
    }

    private List<ProductItem> mapResponsesToItems(List<ProductResponse> apiProducts) {
        List<ProductItem> converted = new ArrayList<>();
        for (ProductResponse pr : apiProducts) {
            long priceVal = pr.getPrice() != null ? pr.getPrice().longValue() : 0L;
            double ratingVal = pr.getRatingAvg() != null ? pr.getRatingAvg() : 5.0;
            int stockVal = pr.getStock() != null ? pr.getStock() : 0;
            String catIdStr = pr.getCategoryId() != null ? String.valueOf(pr.getCategoryId()) : "cat_all";
            String catNameStr = pr.getCategoryName() != null ? pr.getCategoryName() : "Linh kiện";
            String discount = pr.getDiscountBadge() != null ? pr.getDiscountBadge().trim() : "";

            converted.add(new ProductItem(
                    String.valueOf(pr.getId()),
                    catIdStr,
                    catNameStr,
                    pr.getName() != null ? pr.getName() : "Linh kiện Artifex",
                    pr.getDescription() != null && !pr.getDescription().isEmpty()
                            ? pr.getDescription() : "Bảo hành chính hãng 36 tháng",
                    priceVal,
                    discount,
                    pr.getImageUrl() != null ? pr.getImageUrl() : "",
                    ratingVal,
                    stockVal
            ));
        }
        return converted;
    }

    private void appendUniqueProducts(List<ProductItem> incoming) {
        Set<String> existingIds = new HashSet<>();
        for (ProductItem item : allProducts) {
            existingIds.add(item.getId());
        }
        for (ProductItem item : incoming) {
            if (existingIds.add(item.getId())) {
                allProducts.add(item);
            }
        }
    }

    private boolean hasAnyDiscountItem() {
        for (ProductItem item : allProducts) {
            if (item.getDiscountBadge() != null && !item.getDiscountBadge().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void filterAndRenderProducts() {
        if (!hasAnyDiscountItem()) {
            filterDiscountOnly = false;
        }

        List<ProductItem> filtered = new ArrayList<>();
        String keywordLower = currentSearchKeyword.toLowerCase(Locale.ROOT);

        for (ProductItem p : allProducts) {
            if (!"cat_all".equals(selectedCategoryId) && !selectedCategoryId.equals(p.getCategoryId())) {
                continue;
            }

            if (!keywordLower.isEmpty()) {
                boolean matchName = p.getName().toLowerCase(Locale.ROOT).contains(keywordLower);
                boolean matchSpec = p.getShortSpec().toLowerCase(Locale.ROOT).contains(keywordLower);
                boolean matchCat = p.getCategoryLabel().toLowerCase(Locale.ROOT).contains(keywordLower);
                if (!matchName && !matchSpec && !matchCat) {
                    continue;
                }
            }

            long price = p.getPrice();
            if (currentPriceRange == 1 && price >= 5_000_000L) continue;
            if (currentPriceRange == 2 && (price < 5_000_000L || price > 15_000_000L)) continue;
            if (currentPriceRange == 3 && (price < 15_000_000L || price > 30_000_000L)) continue;
            if (currentPriceRange == 4 && price <= 30_000_000L) continue;

            if (filterInStockOnly && p.getStock() <= 0) continue;
            if (filterDiscountOnly && (p.getDiscountBadge() == null || p.getDiscountBadge().isEmpty())) continue;

            filtered.add(p);
        }

        if (currentSortOption == 1) {
            Collections.sort(filtered, Comparator.comparingLong(ProductItem::getPrice));
        } else if (currentSortOption == 2) {
            Collections.sort(filtered, (a, b) -> Long.compare(b.getPrice(), a.getPrice()));
        } else if (currentSortOption == 3) {
            Collections.sort(filtered, (a, b) -> Double.compare(b.getRating(), a.getRating()));
        }

        displayedProducts = filtered;
        productAdapter.updateData(displayedProducts);
        tvResultCount.setText(String.format(Locale.getDefault(), "Hiển thị %d linh kiện", displayedProducts.size()));

        boolean hasActiveFilter = !"cat_all".equals(selectedCategoryId)
                || !currentSearchKeyword.isEmpty()
                || currentSortOption != 0
                || currentPriceRange != 0
                || filterInStockOnly
                || filterDiscountOnly;

        viewFilterDot.setVisibility(
                (currentSortOption != 0 || currentPriceRange != 0 || filterInStockOnly || filterDiscountOnly)
                        ? View.VISIBLE : View.GONE);
        btnQuickResetFilter.setVisibility(hasActiveFilter ? View.VISIBLE : View.GONE);
        tvActiveFilterInfo.setText(getSortSummaryLabel());

        if (displayedProducts.isEmpty()) {
            rvProducts.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvProducts.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void showLoading(boolean show) {
        if (layoutLoading == null || layoutEmptyState == null) return;
        if (show) {
            layoutLoading.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        } else {
            layoutLoading.setVisibility(View.GONE);
        }
    }

    private void setLoadMoreVisible(boolean visible) {
        if (progressLoadMore != null) {
            progressLoadMore.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private String getSortSummaryLabel() {
        switch (currentSortOption) {
            case 1:
                return "Giá: Thấp → Cao";
            case 2:
                return "Giá: Cao → Thấp";
            case 3:
                return "Đánh giá cao";
            default:
                return "Mới nhất";
        }
    }

    private void resetAllFilters() {
        selectedCategoryId = "cat_all";
        currentSearchKeyword = "";
        currentSortOption = 0;
        currentPriceRange = 0;
        filterInStockOnly = false;
        filterDiscountOnly = false;

        editSearch.setText("");
        categoryAdapter.setSelectedPosition(0);
        rvCategories.smoothScrollToPosition(0);
        loadProductsFromApi(0, true);
    }

    private void refreshCatalogData() {
        fetchCategoriesFromApi();
        loadProductsFromApi(0, true);
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), "Đang đồng bộ dữ liệu từ Server...", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_catalog_filter, null, false);
        dialog.setContentView(sheetView);

        TextView btnClose = sheetView.findViewById(R.id.btn_close_filter);
        RadioGroup rgSortOrder = sheetView.findViewById(R.id.rg_sort_order);
        RadioGroup rgPriceRange = sheetView.findViewById(R.id.rg_price_range);
        CheckBox cbInStockOnly = sheetView.findViewById(R.id.cb_in_stock_only);
        CheckBox cbDiscountOnly = sheetView.findViewById(R.id.cb_discount_only);
        MaterialButton btnReset = sheetView.findViewById(R.id.btn_reset_filter);
        MaterialButton btnApply = sheetView.findViewById(R.id.btn_apply_filter);

        if (currentSortOption == 1) rgSortOrder.check(R.id.rb_sort_price_asc);
        else if (currentSortOption == 2) rgSortOrder.check(R.id.rb_sort_price_desc);
        else if (currentSortOption == 3) rgSortOrder.check(R.id.rb_sort_rating_desc);
        else rgSortOrder.check(R.id.rb_sort_default);

        if (currentPriceRange == 1) rgPriceRange.check(R.id.rb_price_under_5m);
        else if (currentPriceRange == 2) rgPriceRange.check(R.id.rb_price_5m_to_15m);
        else if (currentPriceRange == 3) rgPriceRange.check(R.id.rb_price_15m_to_30m);
        else if (currentPriceRange == 4) rgPriceRange.check(R.id.rb_price_over_30m);
        else rgPriceRange.check(R.id.rb_price_all);

        cbInStockOnly.setChecked(filterInStockOnly);

        if (hasAnyDiscountItem()) {
            cbDiscountOnly.setEnabled(true);
            cbDiscountOnly.setAlpha(1.0f);
            cbDiscountOnly.setText("Chỉ hiển thị linh kiện đang giảm giá");
            cbDiscountOnly.setChecked(filterDiscountOnly);
        } else {
            cbDiscountOnly.setEnabled(false);
            cbDiscountOnly.setAlpha(0.5f);
            cbDiscountOnly.setChecked(false);
            cbDiscountOnly.setText("Chỉ hiển thị linh kiện đang giảm giá (Chưa có KM)");
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnReset.setOnClickListener(v -> {
            int previousSort = currentSortOption;
            currentSortOption = 0;
            currentPriceRange = 0;
            filterInStockOnly = false;
            filterDiscountOnly = false;
            if (!isOfflineFallbackMode && previousSort != 0) {
                loadProductsFromApi(0, true);
            } else {
                filterAndRenderProducts();
            }
            dialog.dismiss();
        });

        btnApply.setOnClickListener(v -> {
            int previousSort = currentSortOption;

            int sortId = rgSortOrder.getCheckedRadioButtonId();
            if (sortId == R.id.rb_sort_price_asc) currentSortOption = 1;
            else if (sortId == R.id.rb_sort_price_desc) currentSortOption = 2;
            else if (sortId == R.id.rb_sort_rating_desc) currentSortOption = 3;
            else currentSortOption = 0;

            int priceId = rgPriceRange.getCheckedRadioButtonId();
            if (priceId == R.id.rb_price_under_5m) currentPriceRange = 1;
            else if (priceId == R.id.rb_price_5m_to_15m) currentPriceRange = 2;
            else if (priceId == R.id.rb_price_15m_to_30m) currentPriceRange = 3;
            else if (priceId == R.id.rb_price_over_30m) currentPriceRange = 4;
            else currentPriceRange = 0;

            filterInStockOnly = cbInStockOnly.isChecked();
            filterDiscountOnly = cbDiscountOnly.isEnabled() && cbDiscountOnly.isChecked();

            // Chỉ gọi lại API khi thứ tự sắp xếp thay đổi; lọc giá/tồn kho xử lý trực tiếp trên dữ liệu đã tải
            if (!isOfflineFallbackMode && currentSortOption != previousSort) {
                loadProductsFromApi(0, true);
            } else {
                filterAndRenderProducts();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    // =========================================================================
    // DIALOG THÊM SẢN PHẨM & TẢI ẢNH QUA PRODUCT CONTROLLER (ADMIN ONLY)
    // =========================================================================
    private void showAddProductCloudinaryDialog() {
        if (!isAdded() || getContext() == null) return;
        if (!isAdminUser()) {
            Toast.makeText(getContext(), "Chỉ Quản trị viên (ADMIN) mới có quyền thêm linh kiện!", Toast.LENGTH_SHORT).show();
            updateAdminControlsVisibility();
            return;
        }

        selectedImageUri = null;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_product_cloudinary, null, false);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        MaterialCardView cardPickImage = dialogView.findViewById(R.id.card_pick_image);
        activeDialogPreviewImage = dialogView.findViewById(R.id.iv_preview_product);
        activeDialogPlaceholder = dialogView.findViewById(R.id.layout_pick_placeholder);
        activeDialogStatusText = dialogView.findViewById(R.id.tv_cloudinary_status);

        EditText etName = dialogView.findViewById(R.id.et_new_product_name);
        EditText etSpec = dialogView.findViewById(R.id.et_new_product_spec);
        EditText etPrice = dialogView.findViewById(R.id.et_new_product_price);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel_new_product);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_save_new_product);

        cardPickImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String spec = etSpec.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();

            String nameError = CatalogHelper.validateProductName(name);
            if (nameError != null) {
                etName.setError(nameError);
                etName.requestFocus();
                return;
            }

            String priceError = CatalogHelper.validateProductPrice(priceStr);
            if (priceError != null) {
                etPrice.setError(priceError);
                etPrice.requestFocus();
                return;
            }

            long price = Long.parseLong(priceStr);
            btnSave.setEnabled(false);
            btnSave.setText("Đang lưu...");

            createProductThenUploadImage(name, spec, price, selectedImageUri, dialog, btnSave);
        });

        dialog.setOnDismissListener(d -> {
            activeDialogPreviewImage = null;
            activeDialogPlaceholder = null;
            activeDialogStatusText = null;
        });

        dialog.show();
    }

    private void createProductThenUploadImage(
            String name,
            String spec,
            long price,
            @Nullable Uri imageUri,
            AlertDialog dialog,
            MaterialButton btnSave) {

        Long catId = 1L;
        if (!"cat_all".equals(selectedCategoryId)) {
            try {
                catId = Long.parseLong(selectedCategoryId.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {}
        }

        String initialImageUrl = imageUri == null
                ? "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=800&q=80"
                : "";

        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("price", price);
        body.put("stock", 20);
        body.put("description", spec.isEmpty() ? "Bảo hành 36 tháng chính hãng" : spec);
        body.put("imageUrl", initialImageUrl);
        body.put("categoryId", catId);

        RetrofitClient.getApiService().createProduct(body).enqueue(new Callback<CatalogApiResponse<ProductResponse>>() {
            @Override
            public void onResponse(@NonNull Call<CatalogApiResponse<ProductResponse>> call,
                                   @NonNull Response<CatalogApiResponse<ProductResponse>> response) {
                if (!isAdded() || getContext() == null) return;

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    ProductResponse createdProduct = response.body().getData();
                    if (imageUri != null && createdProduct.getId() != null) {
                        btnSave.setText("Đang tải ảnh...");
                        uploadProductImageToBackend(createdProduct.getId(), imageUri, dialog, btnSave);
                    } else {
                        if (dialog.isShowing()) {
                            dialog.dismiss();
                        }
                        Toast.makeText(getContext(), "Đã thêm linh kiện vào cơ sở dữ liệu!", Toast.LENGTH_SHORT).show();
                        loadProductsFromApi(0, true);
                    }
                } else {
                    btnSave.setEnabled(true);
                    btnSave.setText("Lưu linh kiện");
                    String errMsg = parseRetrofitErrorMessage(response, "Không thể tạo linh kiện (HTTP " + response.code() + ")");
                    Toast.makeText(getContext(), errMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CatalogApiResponse<ProductResponse>> call, @NonNull Throwable t) {
                if (!isAdded() || getContext() == null) return;
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                isOfflineFallbackMode = true;
                ProductItem localNew = new ProductItem(
                        String.valueOf(allProducts.size() + 100),
                        "1",
                        "Linh kiện",
                        name,
                        spec.isEmpty() ? "Bảo hành 36 tháng chính hãng" : spec,
                        price,
                        "MỚI",
                        imageUri != null ? imageUri.toString() : initialImageUrl,
                        5.0,
                        20
                );
                allProducts.add(0, localNew);
                filterAndRenderProducts();
                Toast.makeText(getContext(), "Server chưa phản hồi. Đã thêm tạm (Chế độ ngoại tuyến).", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void uploadProductImageToBackend(
            Long productId,
            Uri imageUri,
            AlertDialog dialog,
            MaterialButton btnSave) {

        Context appContext = requireContext().getApplicationContext();
        if (ioExecutor == null || ioExecutor.isShutdown()) {
            ioExecutor = Executors.newSingleThreadExecutor();
        }

        ioExecutor.execute(() -> {
            byte[] imageBytes;
            String errorReason = null;
            try {
                imageBytes = readImageBytes(appContext, imageUri);
                if (imageBytes == null || imageBytes.length == 0) {
                    errorReason = "Không đọc được nội dung tệp ảnh";
                } else if (imageBytes.length > MAX_IMAGE_BYTES) {
                    errorReason = "Ảnh vượt quá dung lượng tối đa 10MB";
                }
            } catch (IOException e) {
                imageBytes = null;
                errorReason = e.getMessage();
            }

            final byte[] finalBytes = imageBytes;
            final String finalError = errorReason;

            searchHandler.post(() -> {
                if (!isAdded() || getContext() == null) return;
                if (finalBytes == null || finalError != null) {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    Toast.makeText(getContext(),
                            "Đã tạo linh kiện, nhưng lỗi đọc ảnh: " + finalError,
                            Toast.LENGTH_LONG).show();
                    loadProductsFromApi(0, true);
                    return;
                }

                RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), finalBytes);
                MultipartBody.Part part = MultipartBody.Part.createFormData(
                        "file",
                        "product_" + productId + "_" + System.currentTimeMillis() + ".jpg",
                        requestFile
                );

                RetrofitClient.getApiService().uploadProductImage(productId, part)
                        .enqueue(new Callback<CatalogApiResponse<String>>() {
                            @Override
                            public void onResponse(@NonNull Call<CatalogApiResponse<String>> call,
                                                   @NonNull Response<CatalogApiResponse<String>> response) {
                                if (!isAdded() || getContext() == null) return;
                                if (dialog.isShowing()) {
                                    dialog.dismiss();
                                }
                                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                                    Toast.makeText(getContext(),
                                            "Đã thêm linh kiện & tải ảnh lên Cloudinary!",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    String errMsg = parseRetrofitErrorMessage(response, "HTTP " + response.code());
                                    Toast.makeText(getContext(),
                                            "Đã tạo linh kiện, nhưng tải ảnh Cloudinary thất bại: " + errMsg,
                                            Toast.LENGTH_LONG).show();
                                }
                                loadProductsFromApi(0, true);
                            }

                            @Override
                            public void onFailure(@NonNull Call<CatalogApiResponse<String>> call, @NonNull Throwable t) {
                                if (!isAdded() || getContext() == null) return;
                                if (dialog.isShowing()) {
                                    dialog.dismiss();
                                }
                                Toast.makeText(getContext(),
                                        "Đã tạo linh kiện, nhưng lỗi mạng khi tải ảnh: " + t.getMessage(),
                                        Toast.LENGTH_LONG).show();
                                loadProductsFromApi(0, true);
                            }
                        });
            });
        });
    }

    private byte[] readImageBytes(Context context, Uri uri) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                return null;
            }
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }

    private String parseRetrofitErrorMessage(Response<?> response, String defaultMsg) {
        try (ResponseBody errorBody = response.errorBody()) {
            if (errorBody != null) {
                return CatalogHelper.extractErrorMessage(errorBody.string(), defaultMsg);
            }
        } catch (IOException ignored) {
        }
        return defaultMsg;
    }

    private void navigateToProductDetail(ProductItem product) {
        DetailFragment detailFragment = new DetailFragment();
        Bundle args = new Bundle();
        try {
            args.putLong("product_id", Long.parseLong(product.getId().replaceAll("[^0-9]", "")));
        } catch (NumberFormatException e) {
            args.putLong("product_id", 1L);
        }
        args.putString("product_name", product.getName());
        args.putString("product_spec", product.getShortSpec());
        args.putLong("product_price", product.getPrice());
        args.putString("product_image_url", product.getImageUrl());
        detailFragment.setArguments(args);

        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, detailFragment)
                .addToBackStack("catalog_to_detail")
                .commit();
    }

    // =========================================================================
    // DỮ LIỆU DỰ PHÒNG KHI OFFLINE (KHỚP ID VỚI DATABASE/SEED_DATA.SQL)
    // =========================================================================
    private void setupFallbackCategories() {
        categoryList.clear();
        categoryList.add(new CategoryItem("cat_all", "Tất cả"));
        categoryList.add(new CategoryItem("1", "CPU"));
        categoryList.add(new CategoryItem("2", "GPU"));
        categoryList.add(new CategoryItem("3", "RAM"));
        categoryList.add(new CategoryItem("4", "SSD"));
        categoryList.add(new CategoryItem("5", "Mainboard"));
        categoryList.add(new CategoryItem("6", "Case"));
        categoryList.add(new CategoryItem("7", "PSU"));
    }

    private void setupFallbackProducts() {
        allProducts.clear();
        allProducts.add(new ProductItem(
                "1", "1", "CPU",
                "Intel Core i5-13600K",
                "14 nhân 20 luồng, Turbo 5.1 GHz",
                7890000L, "-5%",
                "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?auto=format&fit=crop&w=800&q=80",
                5.0, 25
        ));
        allProducts.add(new ProductItem(
                "2", "1", "CPU",
                "AMD Ryzen 7 7800X3D",
                "3D V-Cache, 8 nhân 16 luồng AM5",
                10490000L, "",
                "https://images.unsplash.com/photo-1555680202-c86f0e12f086?auto=format&fit=crop&w=800&q=80",
                4.8, 15
        ));
        allProducts.add(new ProductItem(
                "4", "2", "GPU",
                "NVIDIA GeForce RTX 4070 Super 12GB",
                "Ada Lovelace, DLSS 3, Ray Tracing",
                16990000L, "-8%",
                "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?auto=format&fit=crop&w=800&q=80",
                5.0, 10
        ));
        allProducts.add(new ProductItem(
                "7", "3", "RAM",
                "Corsair Vengeance RGB DDR5 32GB (2x16GB) 6000MHz",
                "LED RGB 10 vùng, Intel XMP 3.0",
                3190000L, "",
                "https://images.unsplash.com/photo-1562976540-1502c2145186?auto=format&fit=crop&w=800&q=80",
                5.0, 30
        ));
        allProducts.add(new ProductItem(
                "9", "4", "SSD",
                "Samsung 990 Pro 1TB NVMe M.2 PCIe Gen 4.0",
                "Đọc 7.450 MB/s, Ghi 6.900 MB/s",
                2890000L, "-10%",
                "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?auto=format&fit=crop&w=800&q=80",
                5.0, 25
        ));
        allProducts.add(new ProductItem(
                "11", "5", "Mainboard",
                "ASUS ROG STRIX B760-F GAMING WIFI",
                "16+1 Phase, PCIe 5.0, Wi-Fi 6E",
                5490000L, "",
                "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80",
                5.0, 15
        ));
        allProducts.add(new ProductItem(
                "13", "6", "Case",
                "NZXT H5 Flow RGB Black",
                "Mid-Tower mặt lưới, kèm 2 quạt F140",
                2490000L, "",
                "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?auto=format&fit=crop&w=800&q=80",
                5.0, 18
        ));
        allProducts.add(new ProductItem(
                "14", "7", "PSU",
                "Corsair RM850e 850W 80 Plus Gold ATX 3.0",
                "Full Modular, chuẩn PCIe 5.0",
                3190000L, "-12%",
                "https://images.unsplash.com/photo-1600003014755-ba31aa59c4b6?auto=format&fit=crop&w=800&q=80",
                5.0, 20
        ));
    }

    // --- Data Classes ---

    public static class CategoryItem {
        private final String id;
        private final String name;

        public CategoryItem(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    public static class ProductItem {
        private final String id;
        private final String categoryId;
        private final String categoryLabel;
        private final String name;
        private final String shortSpec;
        private final long price;
        private final String discountBadge;
        private final String imageUrl;
        private final double rating;
        private final int stock;

        public ProductItem(String id, String categoryId, String categoryLabel, String name,
                           String shortSpec, long price, String discountBadge,
                           String imageUrl, double rating, int stock) {
            this.id = id;
            this.categoryId = categoryId;
            this.categoryLabel = categoryLabel;
            this.name = name;
            this.shortSpec = shortSpec;
            this.price = price;
            this.discountBadge = discountBadge;
            this.imageUrl = imageUrl;
            this.rating = rating;
            this.stock = stock;
        }

        public String getId() { return id; }
        public String getCategoryId() { return categoryId; }
        public String getCategoryLabel() { return categoryLabel; }
        public String getName() { return name; }
        public String getShortSpec() { return shortSpec; }
        public long getPrice() { return price; }
        public String getDiscountBadge() { return discountBadge; }
        public String getImageUrl() { return imageUrl; }
        public double getRating() { return rating; }
        public int getStock() { return stock; }
    }
}
