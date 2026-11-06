package com.artifex.app.ui.store;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.artifex.app.R;

/**
 * Activity độc lập chứa StoreFragment (Màn hình Lịch sử đơn hàng).
 * Cho phép mở trực tiếp từ mục "Đơn mua" trong AuthFragment (Tài khoản)
 * với bộ lọc trạng thái tương ứng (ALL, PENDING, SHIPPING, COMPLETED, CANCELLED)
 * và hiển thị nút Back để quay về màn hình trước.
 */
public class OrderHistoryActivity extends AppCompatActivity {

    public static final String EXTRA_STATUS_FILTER = "extra_status_filter";

    public static void start(Context context, String statusFilter) {
        Intent intent = new Intent(context, OrderHistoryActivity.class);
        intent.putExtra(EXTRA_STATUS_FILTER, statusFilter != null ? statusFilter : StoreFragment.FILTER_ALL);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_order_history);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.order_history_fragment_container), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            String filter = getIntent().getStringExtra(EXTRA_STATUS_FILTER);
            if (filter == null) {
                filter = StoreFragment.FILTER_ALL;
            }
            StoreFragment fragment = StoreFragment.newInstance(filter, true);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.order_history_fragment_container, fragment)
                    .commit();
        }
    }
}
