package com.artifex.app.ui.catalog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.R;

import java.util.ArrayList;
import java.util.List;

public class CatalogFragment extends Fragment {

    private RecyclerView rvCategories;
    private RecyclerView rvProducts;
    private EditText editSearch;
    private ImageButton btnFilter;

    private CategoryAdapter categoryAdapter;
    private ProductAdapter productAdapter;

    private List<CategoryItem> categoryList;
    private List<ProductItem> allProducts;

    public CatalogFragment() {
        // Required empty public constructor
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
        btnFilter = view.findViewById(R.id.btn_filter);

        // 2. Setup dữ liệu Mock
        setupMockData();

        // 3. Setup Categories RecyclerView
        categoryAdapter = new CategoryAdapter(categoryList, (category, position) -> {
            filterProductsByCategory(category.getId());
        });
        rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);

        // 4. Setup Products RecyclerView (Grid 2 cột)
        productAdapter = new ProductAdapter(allProducts, product -> {
            // Xử lý sự kiện click Card linh kiện, chuẩn bị truyền productId sang DetailFragment
            Toast.makeText(requireContext(), "Đã chọn: " + product.getName(), Toast.LENGTH_SHORT).show();
            // TODO: Mở DetailFragment và truyền product.getId() qua Bundle arguments
        });
        rvProducts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rvProducts.setAdapter(productAdapter);

        // 5. Setup Filter Button
        btnFilter.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Mở bộ lọc", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupMockData() {
        // Dữ liệu danh mục linh kiện
        categoryList = new ArrayList<>();
        categoryList.add(new CategoryItem("cat_all", "Tất cả"));
        categoryList.add(new CategoryItem("cat_cpu", "CPU"));
        categoryList.add(new CategoryItem("cat_vga", "VGA"));
        categoryList.add(new CategoryItem("cat_main", "Mainboard"));
        categoryList.add(new CategoryItem("cat_ram", "RAM"));
        categoryList.add(new CategoryItem("cat_hdd", "Ổ cứng"));
        categoryList.add(new CategoryItem("cat_psu", "Nguồn"));
        categoryList.add(new CategoryItem("cat_case", "Case"));
        categoryList.add(new CategoryItem("cat_gear", "Gear"));

        // Dữ liệu linh kiện PC (6-8 sản phẩm thực tế)
        allProducts = new ArrayList<>();
        allProducts.add(new ProductItem(
                "p1",
                "cat_cpu",
                "CPU Intel Core i9-14900K",
                "24 Cores, 32 Threads, 6.0 GHz",
                14500000,
                "-5%",
                "https://example.com/cpu.jpg" // Glide sẽ hiển thị placeholder khi load lỗi
        ));
        allProducts.add(new ProductItem(
                "p2",
                "cat_vga",
                "VGA ASUS ROG Strix RTX 4090",
                "24GB GDDR6X, 384-bit",
                55990000,
                "",
                "https://example.com/vga.jpg"
        ));
        allProducts.add(new ProductItem(
                "p3",
                "cat_main",
                "Mainboard GIGABYTE Z790 AORUS",
                "LGA 1700, DDR5, ATX",
                7200000,
                "-10%",
                "https://example.com/main.jpg"
        ));
        allProducts.add(new ProductItem(
                "p4",
                "cat_ram",
                "RAM Corsair Dominator Platinum",
                "2x16GB, DDR5 6000MHz",
                4500000,
                "",
                "https://example.com/ram.jpg"
        ));
        allProducts.add(new ProductItem(
                "p5",
                "cat_hdd",
                "SSD Samsung 990 PRO 2TB",
                "M.2 NVMe, PCIe 4.0",
                4800000,
                "-15%",
                "https://example.com/ssd.jpg"
        ));
        allProducts.add(new ProductItem(
                "p6",
                "cat_psu",
                "Nguồn Corsair RM1000x",
                "1000W, 80 Plus Gold",
                4200000,
                "",
                "https://example.com/psu.jpg"
        ));
        allProducts.add(new ProductItem(
                "p7",
                "cat_case",
                "Case NZXT H9 Flow Black",
                "Mid Tower, Kính cường lực",
                3990000,
                "",
                "https://example.com/case.jpg"
        ));
        allProducts.add(new ProductItem(
                "p8",
                "cat_gear",
                "Bàn phím cơ Razer BlackWidow",
                "Green Switch, RGB",
                3800000,
                "-8%",
                "https://example.com/keyboard.jpg"
        ));
    }

    private void filterProductsByCategory(String categoryId) {
        if ("cat_all".equals(categoryId)) {
            productAdapter.updateData(allProducts);
            return;
        }
        
        List<ProductItem> filtered = new ArrayList<>();
        for (ProductItem p : allProducts) {
            if (categoryId.equals(p.getCategoryId())) {
                filtered.add(p);
            }
        }
        productAdapter.updateData(filtered);
    }

    // --- Data Classes ---

    public static class CategoryItem {
        private String id;
        private String name;

        public CategoryItem(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    public static class ProductItem {
        private String id;
        private String categoryId;
        private String name;
        private String shortSpec;
        private long price;
        private String discountBadge;
        private String imageUrl;

        public ProductItem(String id, String categoryId, String name, String shortSpec, long price, String discountBadge, String imageUrl) {
            this.id = id;
            this.categoryId = categoryId;
            this.name = name;
            this.shortSpec = shortSpec;
            this.price = price;
            this.discountBadge = discountBadge;
            this.imageUrl = imageUrl;
        }

        public String getId() { return id; }
        public String getCategoryId() { return categoryId; }
        public String getName() { return name; }
        public String getShortSpec() { return shortSpec; }
        public long getPrice() { return price; }
        public String getDiscountBadge() { return discountBadge; }
        public String getImageUrl() { return imageUrl; }
    }
}