package com.artifex.app.ui.catalog;

import android.app.AlertDialog;
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
import com.artifex.app.ui.detail.DetailFragment;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CatalogFragment extends Fragment {

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

    // --- Adapters & Data ---
    private CategoryAdapter categoryAdapter;
    private ProductAdapter productAdapter;
    private List<CategoryItem> categoryList;
    private List<ProductItem> allProducts;

    // --- Filter & Sort State ---
    private String selectedCategoryId = "cat_all";
    private String currentSearchKeyword = "";
    private int currentSortOption = 0; // 0: Default, 1: Price ASC, 2: Price DESC, 3: Rating DESC
    private int currentPriceRange = 0; // 0: All, 1: <5M, 2: 5M-15M, 3: 15M-30M, 4: >30M
    private boolean filterInStockOnly = false;
    private boolean filterDiscountOnly = false;

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

        // Đăng ký trình chọn ảnh từ thư viện thiết bị (Demo Cloudinary Upload)
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        if (activeDialogPreviewImage != null) {
                            Glide.with(requireContext())
                                    .load(uri)
                                    .centerCrop()
                                    .into(activeDialogPreviewImage);
                        }
                        if (activeDialogPlaceholder != null) {
                            activeDialogPlaceholder.setVisibility(View.GONE);
                        }
                        if (activeDialogStatusText != null) {
                            activeDialogStatusText.setText("Đã chọn ảnh sẵn sàng tải lên Cloudinary");
                        }
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

        // 1. Ánh xạ View (findViewById truyền thống)
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

        // 2. Khởi tạo dữ liệu linh kiện
        setupMockData();

        // 3. Setup Categories RecyclerView (Horizontal)
        categoryAdapter = new CategoryAdapter(categoryList, (category, position) -> {
            selectedCategoryId = category.getId();
            applyFiltersAndSort();
        });
        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);

        // 4. Setup Products RecyclerView (Grid 2 cột)
        productAdapter = new ProductAdapter(new ArrayList<>(allProducts), this::navigateToProductDetail);
        rvProducts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvProducts.setAdapter(productAdapter);

        // 5. Setup Search Bar (TextWatcher + Clear Button)
        setupSearchEvents();

        // 6. Setup Filter Button, Reset Buttons, Refresh & Cloudinary Add Product
        btnFilter.setOnClickListener(v -> showFilterBottomSheet());
        btnQuickResetFilter.setOnClickListener(v -> resetAllFilters());
        btnEmptyReset.setOnClickListener(v -> resetAllFilters());
        btnRefreshCatalog.setOnClickListener(v -> refreshCatalogData());
        btnAddProductCloudinary.setOnClickListener(v -> showAddProductCloudinaryDialog());

        // 7. Cập nhật danh sách lần đầu
        applyFiltersAndSort();
    }

    private void setupSearchEvents() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchKeyword = s != null ? s.toString().trim() : "";
                btnClearSearch.setVisibility(currentSearchKeyword.isEmpty() ? View.GONE : View.VISIBLE);
                applyFiltersAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyFiltersAndSort();
                return true;
            }
            return false;
        });

        btnClearSearch.setOnClickListener(v -> {
            editSearch.setText("");
            editSearch.clearFocus();
        });
    }

    private void applyFiltersAndSort() {
        List<ProductItem> filtered = new ArrayList<>();
        String keywordLower = currentSearchKeyword.toLowerCase(Locale.ROOT);

        for (ProductItem p : allProducts) {
            // 1. Lọc theo Category
            if (!"cat_all".equals(selectedCategoryId) && !selectedCategoryId.equals(p.getCategoryId())) {
                continue;
            }

            // 2. Lọc theo từ khóa tìm kiếm (Tên, thông số, danh mục)
            if (!keywordLower.isEmpty()) {
                boolean matchName = p.getName().toLowerCase(Locale.ROOT).contains(keywordLower);
                boolean matchSpec = p.getShortSpec().toLowerCase(Locale.ROOT).contains(keywordLower);
                boolean matchCat = p.getCategoryLabel().toLowerCase(Locale.ROOT).contains(keywordLower);
                if (!matchName && !matchSpec && !matchCat) {
                    continue;
                }
            }

            // 3. Lọc theo khoảng giá
            long price = p.getPrice();
            if (currentPriceRange == 1 && price >= 5_000_000L) continue;
            if (currentPriceRange == 2 && (price < 5_000_000L || price > 15_000_000L)) continue;
            if (currentPriceRange == 3 && (price < 15_000_000L || price > 30_000_000L)) continue;
            if (currentPriceRange == 4 && price <= 30_000_000L) continue;

            // 4. Lọc theo tồn kho & giảm giá
            if (filterInStockOnly && p.getStock() <= 0) continue;
            if (filterDiscountOnly && (p.getDiscountBadge() == null || p.getDiscountBadge().isEmpty())) continue;

            filtered.add(p);
        }

        // 5. Sắp xếp danh sách
        if (currentSortOption == 1) {
            Collections.sort(filtered, Comparator.comparingLong(ProductItem::getPrice));
        } else if (currentSortOption == 2) {
            Collections.sort(filtered, (a, b) -> Long.compare(b.getPrice(), a.getPrice()));
        } else if (currentSortOption == 3) {
            Collections.sort(filtered, (a, b) -> Double.compare(b.getRating(), a.getRating()));
        }

        // 6. Cập nhật UI
        productAdapter.updateData(filtered);
        tvResultCount.setText(String.format(Locale.getDefault(), "Hiển thị %d linh kiện", filtered.size()));

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

        if (filtered.isEmpty()) {
            rvProducts.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        } else {
            rvProducts.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
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
        applyFiltersAndSort();
    }

    private void refreshCatalogData() {
        rvProducts.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.GONE);
        layoutLoading.setVisibility(View.VISIBLE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isAdded()) return;
            layoutLoading.setVisibility(View.GONE);
            applyFiltersAndSort();
            Toast.makeText(requireContext(), "Đã làm mới danh mục linh kiện", Toast.LENGTH_SHORT).show();
        }, 450);
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

        // Khôi phục trạng thái hiện tại
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
        cbDiscountOnly.setChecked(filterDiscountOnly);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnReset.setOnClickListener(v -> {
            currentSortOption = 0;
            currentPriceRange = 0;
            filterInStockOnly = false;
            filterDiscountOnly = false;
            applyFiltersAndSort();
            dialog.dismiss();
        });

        btnApply.setOnClickListener(v -> {
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
            filterDiscountOnly = cbDiscountOnly.isChecked();

            applyFiltersAndSort();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showAddProductCloudinaryDialog() {
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

            if (name.isEmpty()) {
                etName.setError("Vui lòng nhập tên linh kiện");
                return;
            }
            if (priceStr.isEmpty()) {
                etPrice.setError("Vui lòng nhập giá bán");
                return;
            }

            long price;
            try {
                price = Long.parseLong(priceStr);
            } catch (NumberFormatException e) {
                etPrice.setError("Giá bán không hợp lệ");
                return;
            }

            String catId = "cat_all".equals(selectedCategoryId) ? "cat_vga" : selectedCategoryId;
            String catLabel = getCategoryLabelById(catId);
            String imageUrl = selectedImageUri != null
                    ? selectedImageUri.toString()
                    : "https://res.cloudinary.com/demo/image/upload/sample.jpg";

            ProductItem newProduct = new ProductItem(
                    String.valueOf(allProducts.size() + 1),
                    catId,
                    catLabel,
                    name,
                    spec.isEmpty() ? "Chính hãng Artifex Bảo hành 36T" : spec,
                    price,
                    "MỚI",
                    imageUrl,
                    5.0,
                    10
            );

            allProducts.add(0, newProduct);
            applyFiltersAndSort();
            rvProducts.smoothScrollToPosition(0);
            Toast.makeText(requireContext(), "Đã thêm linh kiện & cập nhật ảnh!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> {
            activeDialogPreviewImage = null;
            activeDialogPlaceholder = null;
            activeDialogStatusText = null;
        });

        dialog.show();
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

    private String getCategoryLabelById(String categoryId) {
        for (CategoryItem item : categoryList) {
            if (item.getId().equals(categoryId)) {
                return item.getName();
            }
        }
        return "Linh kiện";
    }

    private void setupMockData() {
        categoryList = new ArrayList<>();
        categoryList.add(new CategoryItem("cat_all", "Tất cả"));
        categoryList.add(new CategoryItem("cat_cpu", "CPU"));
        categoryList.add(new CategoryItem("cat_vga", "VGA"));
        categoryList.add(new CategoryItem("cat_main", "Mainboard"));
        categoryList.add(new CategoryItem("cat_ram", "RAM"));
        categoryList.add(new CategoryItem("cat_hdd", "Ổ cứng SSD"));
        categoryList.add(new CategoryItem("cat_psu", "Nguồn PSU"));
        categoryList.add(new CategoryItem("cat_case", "Vỏ Case"));
        categoryList.add(new CategoryItem("cat_gear", "Gaming Gear"));

        allProducts = new ArrayList<>();
        allProducts.add(new ProductItem(
                "1", "cat_cpu", "CPU",
                "CPU Intel Core i9-14900K",
                "24 Nhân 32 Luồng, Boost 6.0GHz, LGA1700",
                14500000L, "-5%",
                "https://images.unsplash.com/photo-1591799264318-7e6ef8ddb7ea?w=600",
                4.9, 15
        ));
        allProducts.add(new ProductItem(
                "2", "cat_vga", "VGA",
                "VGA ASUS ROG Strix GeForce RTX 4090 OC",
                "24GB GDDR6X, 384-bit, DLSS 3",
                55990000L, "-8%",
                "https://images.unsplash.com/photo-1587202372775-e229f172b9d7?w=600",
                5.0, 5
        ));
        allProducts.add(new ProductItem(
                "3", "cat_main", "Mainboard",
                "Mainboard GIGABYTE Z790 AORUS ELITE AX",
                "Socket LGA1700, DDR5, PCIe 5.0, Wi-Fi 6E",
                7200000L, "-10%",
                "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600",
                4.8, 12
        ));
        allProducts.add(new ProductItem(
                "4", "cat_ram", "RAM",
                "RAM Corsair Dominator Platinum RGB 32GB",
                "2x16GB DDR5 Bus 6000MHz, Cas 30",
                4500000L, "",
                "https://images.unsplash.com/photo-1562976540-1502c2145186?w=600",
                4.7, 25
        ));
        allProducts.add(new ProductItem(
                "5", "cat_hdd", "Ổ cứng SSD",
                "SSD Samsung 990 PRO 2TB NVMe M.2",
                "PCIe Gen 4x4, Đọc 7450MB/s, Ghi 6900MB/s",
                4800000L, "-15%",
                "https://images.unsplash.com/photo-1597872200969-2b65d56bd16b?w=600",
                4.9, 30
        ));
        allProducts.add(new ProductItem(
                "6", "cat_psu", "Nguồn PSU",
                "Nguồn Corsair RM1000e ATX 3.0 1000W",
                "80 Plus Gold, Full Modular, PCIe 5.0",
                4200000L, "",
                "https://images.unsplash.com/photo-1587202372634-32705e3bf49c?w=600",
                4.6, 8
        ));
        allProducts.add(new ProductItem(
                "7", "cat_case", "Vỏ Case",
                "Case NZXT H9 Flow Dual-Chamber Black",
                "Mid Tower, Kính cường lực vô cực, 4 Fan",
                3990000L, "",
                "https://images.unsplash.com/photo-1587831990711-23ca6441447b?w=600",
                4.8, 0
        ));
        allProducts.add(new ProductItem(
                "8", "cat_gear", "Gaming Gear",
                "Bàn phím cơ Razer BlackWidow V4 Pro",
                "Green Switch, Chroma RGB, Núm xoay Command",
                5490000L, "-12%",
                "https://images.unsplash.com/photo-1541140532154-b024d705b90a?w=600",
                4.9, 18
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
