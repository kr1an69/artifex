package com.artifex.app;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.artifex.app.ui.auth.AuthFragment;
import com.artifex.app.ui.cart.CartFragment;
import com.artifex.app.ui.catalog.CatalogFragment;
import com.artifex.app.ui.detail.DetailFragment;
import com.artifex.app.ui.store.StoreFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Xử lý Window Insets cho EdgeToEdge (Status bar & Navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            bottomNavigationView.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        // Thiết lập sự kiện chuyển tab
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_catalog) {
                selectedFragment = new CatalogFragment();
            } else if (itemId == R.id.nav_store) {
                selectedFragment = new StoreFragment();
            } else if (itemId == R.id.nav_cart) {
                selectedFragment = new CartFragment();
            } else if (itemId == R.id.nav_detail) {
                selectedFragment = new DetailFragment();
            } else if (itemId == R.id.nav_auth) {
                selectedFragment = new AuthFragment();
            }

            if (selectedFragment != null) {
                loadFragment(selectedFragment);
                return true;
            }
            return false;
        });

        // Khởi tạo màn hình mặc định là Catalog (Bạn 2) khi vừa mở app
        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_catalog);
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}