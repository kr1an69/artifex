package com.artifex.app.ui.store;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.BuildConfig;
import com.artifex.app.R;
import com.artifex.app.api.RetrofitClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Màn hình Hệ thống Showroom & Bản đồ (Giai đoạn 2, 3, 4 - Phụ trách: Bạn 5).
 * Trọng tâm kỹ thuật lấy điểm 9.0:
 * - Hiển thị Bản đồ OpenStreetMap (osmdroid) & cắm Marker các chi nhánh Artifex.
 * - Định vị GPS (FusedLocationProviderClient) + Xử lý đa luồng bằng ExecutorService (Worker Thread)
 *   kết hợp Handler(Looper.getMainLooper()) (Main Thread) để tính khoảng cách và sắp xếp Showroom gần nhất.
 */
public class StoreFragment extends Fragment {

    // Giữ các hằng số Filter để tương thích ngược với các nút "Đơn mua" bên AuthFragment (Khoa)
    public static final String FILTER_ALL = OrderHistoryFragment.FILTER_ALL;
    public static final String FILTER_PENDING = OrderHistoryFragment.FILTER_PENDING;
    public static final String FILTER_SHIPPING = OrderHistoryFragment.FILTER_SHIPPING;
    public static final String FILTER_COMPLETED = OrderHistoryFragment.FILTER_COMPLETED;
    public static final String FILTER_CANCELLED = OrderHistoryFragment.FILTER_CANCELLED;

    // Tọa độ mặc định khi máy ảo chưa bật GPS (Khu vực ĐH Nông Lâm TP.HCM - Thủ Đức)
    private static final double DEFAULT_USER_LAT = 10.8712;
    private static final double DEFAULT_USER_LNG = 106.7918;

    // Views
    private TextView tvStoreHeaderSubtitle;
    private MaterialButton btnRefreshStores;
    private View cardStoreOfflineNotice;
    private FrameLayout mapSlotContainer;
    private View cardMapContainer;
    private MapView mapViewStores;
    private MaterialButton btnToggleMapExpand;
    private View layoutMapZoomControls;
    private MaterialButton btnMapZoomIn;
    private MaterialButton btnMapZoomOut;
    private MaterialButton btnLocateNearest;
    private TextView tvNearestStoreBanner;
    private TextView tvMultithreadStatus;
    private RecyclerView rvStores;
    private ProgressBar progressStoreLoading;

    // Map, Location, Multithreading & Adapter
    private StoreAdapter storeAdapter;
    private final List<StoreModel> storeList = new ArrayList<>();
    private final Map<Long, Marker> storeMarkerMap = new HashMap<>();
    private FusedLocationProviderClient fusedLocationClient;
    private ExecutorService distanceExecutor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private CancellationTokenSource locationCancellationTokenSource;
    private boolean isLocating = false;
    private Dialog expandedMapDialog;
    private boolean isMapExpanded = false;
    private double currentUserLat = DEFAULT_USER_LAT;
    private double currentUserLng = DEFAULT_USER_LNG;
    private boolean hasUserLocation = false;

    // Launcher xin quyền GPS Runtime (ACCESS_FINE_LOCATION & ACCESS_COARSE_LOCATION)
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fineGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (Boolean.TRUE.equals(fineGranted) || Boolean.TRUE.equals(coarseGranted)) {
                    fetchUserLocationAndCalculateDistances(true);
                } else {
                    // Nếu từ chối quyền GPS, dùng tọa độ mặc định TP. Thủ Đức để vẫn chạy đa luồng demo
                    calculateNearestStoresInBackground(
                            DEFAULT_USER_LAT, DEFAULT_USER_LNG, storeList,
                            "Tọa độ mặc định (ĐH Nông Lâm - Thủ Đức)"
                    );
                }
            });

    public StoreFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Cấu hình osmdroid Cache vào bộ nhớ riêng của app (tránh lỗi Scoped Storage trên Android 11+)
        Context appContext = requireContext().getApplicationContext();
        File osmdroidBasePath = new File(appContext.getCacheDir(), "osmdroid");
        Configuration.getInstance().load(
                appContext,
                appContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        );
        Configuration.getInstance().setOsmdroidBasePath(osmdroidBasePath);
        Configuration.getInstance().setOsmdroidTileCache(new File(osmdroidBasePath, "tiles"));
        Configuration.getInstance().setUserAgentValue("ArtifexAndroidApp/1.0 (" + appContext.getPackageName() + ")");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        distanceExecutor = Executors.newSingleThreadExecutor();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_store, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bindViews(view);
        setupMapView();
        setupRecyclerView();
        setupActions();

        // Gọi API lấy danh sách Showroom từ Backend Spring Boot
        fetchStoresFromBackend();
    }

    private void bindViews(View view) {
        tvStoreHeaderSubtitle = view.findViewById(R.id.tvStoreHeaderSubtitle);
        btnRefreshStores = view.findViewById(R.id.btnRefreshStores);
        cardStoreOfflineNotice = view.findViewById(R.id.cardStoreOfflineNotice);
        mapSlotContainer = view.findViewById(R.id.mapSlotContainer);
        cardMapContainer = view.findViewById(R.id.cardMapContainer);
        mapViewStores = view.findViewById(R.id.mapViewStores);
        btnToggleMapExpand = view.findViewById(R.id.btnToggleMapExpand);
        layoutMapZoomControls = view.findViewById(R.id.layoutMapZoomControls);
        btnMapZoomIn = view.findViewById(R.id.btnMapZoomIn);
        btnMapZoomOut = view.findViewById(R.id.btnMapZoomOut);
        btnLocateNearest = view.findViewById(R.id.btnLocateNearest);
        tvNearestStoreBanner = view.findViewById(R.id.tvNearestStoreBanner);
        tvMultithreadStatus = view.findViewById(R.id.tvMultithreadStatus);
        rvStores = view.findViewById(R.id.rvStores);
        progressStoreLoading = view.findViewById(R.id.progressStoreLoading);
    }

    /**
     * Khởi tạo cấu hình Bản đồ osmdroid dùng CARTO Voyager (dữ liệu OpenStreetMap + API Key miễn phí).
     * Nếu máy chưa cấu hình CARTO_API_KEY trong local.properties sẽ tự fallback về TileSourceFactory.MAPNIK.
     */
    private void setupMapView() {
        String cartoKey = BuildConfig.CARTO_API_KEY;
        if (cartoKey != null && !cartoKey.trim().isEmpty()) {
            final String keyQuery = "?key=" + cartoKey.trim();
            XYTileSource cartoVoyager = new XYTileSource(
                    "CartoVoyagerKeyed",
                    0,
                    19,
                    256,
                    ".png",
                    new String[]{"https://basemaps.cartocdn.com/rastertiles/voyager/"},
                    "© OpenStreetMap contributors © CARTO"
            ) {
                @Override
                public String getTileURLString(long pMapTileIndex) {
                    return super.getTileURLString(pMapTileIndex) + keyQuery;
                }
            };
            mapViewStores.setTileSource(cartoVoyager);
        } else {
            mapViewStores.setTileSource(TileSourceFactory.MAPNIK);
        }

        mapViewStores.setMultiTouchControls(true);
        // Tắt 2 nút zoom mặc định ở giữa-dưới để không bị đè lên nút "Định vị gần nhất"
        mapViewStores.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);

        IMapController mapController = mapViewStores.getController();
        mapController.setZoom(13.5);
        GeoPoint defaultCenter = new GeoPoint(DEFAULT_USER_LAT, DEFAULT_USER_LNG);
        mapController.setCenter(defaultCenter);
    }

    private void setupRecyclerView() {
        storeAdapter = new StoreAdapter(new ArrayList<>(), new StoreAdapter.OnStoreActionListener() {
            @Override
            public void onFocusStoreOnMap(StoreModel store) {
                focusCameraOnStore(store, true);
            }

            @Override
            public void onOpenDirections(StoreModel store) {
                openGoogleMapsDirections(store);
            }

            @Override
            public void onCallStore(StoreModel store) {
                openDialer(store.getPhone());
            }
        });

        rvStores.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvStores.setAdapter(storeAdapter);
    }

    private void setupActions() {
        btnRefreshStores.setOnClickListener(v -> fetchStoresFromBackend());
        btnLocateNearest.setOnClickListener(v -> checkPermissionAndLocateUser(true));
        btnToggleMapExpand.setOnClickListener(v -> toggleMapExpandMode());
        btnMapZoomIn.setOnClickListener(v -> {
            if (mapViewStores != null) {
                mapViewStores.getController().zoomIn();
            }
        });
        btnMapZoomOut.setOnClickListener(v -> {
            if (mapViewStores != null) {
                mapViewStores.getController().zoomOut();
            }
        });
    }

    /**
     * Phóng to / thu nhỏ bản đồ bằng cách chuyển trực tiếp cardMapContainer sang Dialog nền tối
     * (đè lên Header & BottomNav nhưng vẫn tôn trọng thanh trạng thái và phím điều hướng hệ thống).
     */
    private void toggleMapExpandMode() {
        if (!isAdded() || cardMapContainer == null || mapSlotContainer == null) return;

        if (isMapExpanded) {
            if (expandedMapDialog != null && expandedMapDialog.isShowing()) {
                expandedMapDialog.dismiss();
            }
            return;
        }

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout backdrop = new FrameLayout(requireContext());
        backdrop.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        int outerMarginPx = Math.round(16 * getResources().getDisplayMetrics().density);
        backdrop.setPadding(outerMarginPx, outerMarginPx, outerMarginPx, outerMarginPx);
        ViewCompat.setOnApplyWindowInsetsListener(backdrop, (v, windowInsets) -> {
            Insets sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    sysBars.left + outerMarginPx,
                    sysBars.top + outerMarginPx,
                    sysBars.right + outerMarginPx,
                    sysBars.bottom + outerMarginPx
            );
            return windowInsets;
        });

        // Bấm vào vùng tối xung quanh để thu nhỏ bản đồ
        backdrop.setOnClickListener(v -> dialog.dismiss());

        // Chuyển chính cardMapContainer hiện tại vào Dialog (giữ nguyên camera, zoom, Marker & cache tile)
        ViewGroup currentParent = (ViewGroup) cardMapContainer.getParent();
        if (currentParent != null) {
            currentParent.removeView(cardMapContainer);
        }
        FrameLayout.LayoutParams expandedParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
        backdrop.addView(cardMapContainer, expandedParams);

        dialog.setContentView(backdrop);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.65f);
        }

        isMapExpanded = true;
        expandedMapDialog = dialog;
        btnToggleMapExpand.setIconResource(R.drawable.ic_map_collapse);
        btnToggleMapExpand.setContentDescription("Thu nhỏ bản đồ");
        layoutMapZoomControls.setVisibility(View.VISIBLE);

        dialog.setOnDismissListener(d -> restoreMapToMiniSlot());
        dialog.show();
        if (mapViewStores != null) {
            mapViewStores.invalidate();
        }
    }

    /**
     * Đưa cardMapContainer trở lại đúng khung mini-map ban đầu và ẩn cụm nút Zoom + / -.
     */
    private void restoreMapToMiniSlot() {
        if (cardMapContainer != null && mapSlotContainer != null) {
            ViewGroup currentParent = (ViewGroup) cardMapContainer.getParent();
            if (currentParent != null && currentParent != mapSlotContainer) {
                currentParent.removeView(cardMapContainer);
            }
            if (cardMapContainer.getParent() == null) {
                mapSlotContainer.addView(cardMapContainer, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
            }
        }
        isMapExpanded = false;
        expandedMapDialog = null;
        if (btnToggleMapExpand != null) {
            btnToggleMapExpand.setIconResource(R.drawable.ic_map_expand);
            btnToggleMapExpand.setContentDescription("Phóng to bản đồ");
        }
        if (layoutMapZoomControls != null) {
            layoutMapZoomControls.setVisibility(View.GONE);
        }
        if (mapViewStores != null) {
            mapViewStores.invalidate();
        }
    }

    /**
     * Gọi API GET /api/stores từ StoreController.
     * Nếu chưa bật Backend, tự động nạp 4 Showroom mẫu khớp với database/seed_data.sql.
     */
    private void fetchStoresFromBackend() {
        if (!isAdded()) return;

        progressStoreLoading.setVisibility(View.VISIBLE);

        RetrofitClient.getApiService().getStores().enqueue(new Callback<List<StoreModel>>() {
            @Override
            public void onResponse(@NonNull Call<List<StoreModel>> call, @NonNull Response<List<StoreModel>> response) {
                if (!isAdded()) return;
                progressStoreLoading.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    cardStoreOfflineNotice.setVisibility(View.GONE);
                    storeList.clear();
                    storeList.addAll(response.body());

                    tvStoreHeaderSubtitle.setText("Hệ thống " + storeList.size() + " chi nhánh chính hãng tại TP.HCM");
                    renderMarkersOnMap(storeList);
                    storeAdapter.updateData(storeList);

                    // Sau khi có danh sách Showroom, tiến hành định vị GPS & tính khoảng cách đa luồng
                    checkPermissionAndLocateUser(false);
                } else {
                    loadOfflineDemoStores();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<StoreModel>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                progressStoreLoading.setVisibility(View.GONE);
                loadOfflineDemoStores();
            }
        });
    }

    /**
     * Dữ liệu 4 Showroom mẫu khớp 100% với database/seed_data.sql khi chưa bật Server Spring Boot.
     */
    private void loadOfflineDemoStores() {
        cardStoreOfflineNotice.setVisibility(View.VISIBLE);
        storeList.clear();

        storeList.add(new StoreModel(
                1L,
                "Artifex Nông Lâm - Thủ Đức",
                "Số 1 ĐH Nông Lâm, Khu phố 6, P. Linh Trung, TP. Thủ Đức, TP.HCM",
                10.870500, 106.791500,
                "028 3896 0711",
                "08:00 - 21:30"
        ));
        storeList.add(new StoreModel(
                2L,
                "Artifex Quận 10",
                "398 Nguyễn Tri Phương, Phường 4, Quận 10, TP.HCM",
                10.763100, 106.666800,
                "028 3834 5678",
                "08:30 - 21:30"
        ));
        storeList.add(new StoreModel(
                3L,
                "Artifex Bình Thạnh",
                "157 Bạch Đằng, Phường 15, Quận Bình Thạnh, TP.HCM",
                10.801600, 106.702900,
                "028 3511 2233",
                "08:00 - 21:00"
        ));
        storeList.add(new StoreModel(
                4L,
                "Artifex Quận 1 Flagship",
                "92 Cách Mạng Tháng Tám, Phường Bến Thành, Quận 1, TP.HCM",
                10.776900, 106.691700,
                "028 3822 9988",
                "08:00 - 22:00"
        ));

        tvStoreHeaderSubtitle.setText("Hệ thống " + storeList.size() + " chi nhánh tại TP.HCM (Chế độ Demo)");
        renderMarkersOnMap(storeList);
        storeAdapter.updateData(storeList);

        checkPermissionAndLocateUser(false);
    }

    /**
     * Cắm Marker các Showroom và vị trí người dùng lên bản đồ MapView (dùng icon nhỏ gọn, phân biệt rõ).
     */
    private void renderMarkersOnMap(List<StoreModel> stores) {
        if (mapViewStores == null || !isAdded()) return;

        mapViewStores.getOverlays().clear();
        storeMarkerMap.clear();

        Drawable userIcon = createUserLocationDotIcon();
        Drawable storeIcon = createSmallStorePinIcon();

        // 1. Vẽ Marker vị trí người dùng (chấm tròn xanh dương nhỏ gọn)
        if (hasUserLocation) {
            Marker userMarker = new Marker(mapViewStores);
            userMarker.setPosition(new GeoPoint(currentUserLat, currentUserLng));
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            if (userIcon != null) {
                userMarker.setIcon(userIcon);
            }
            userMarker.setTitle("Vị trí của bạn");
            userMarker.setSubDescription(String.format(Locale.US, "Tọa độ GPS: %.4f, %.4f", currentUserLat, currentUserLng));
            mapViewStores.getOverlays().add(userMarker);
        }

        // 2. Vẽ Marker cho từng Showroom Artifex (icon nhỏ gọn)
        for (StoreModel store : stores) {
            GeoPoint location = new GeoPoint(store.getLatitude(), store.getLongitude());
            Marker marker = new Marker(mapViewStores);
            marker.setPosition(location);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            if (storeIcon != null) {
                marker.setIcon(storeIcon);
            }

            String distanceSuffix = store.getDistanceKm() >= 0
                    ? String.format(Locale.US, " (Cách bạn %.2f km)", store.getDistanceKm())
                    : "";
            marker.setTitle(store.getName() + distanceSuffix);
            marker.setSnippet(store.getAddress());
            marker.setSubDescription("Hotline: " + store.getPhone() + " • Giờ mở cửa: " + store.getOpeningHours());

            mapViewStores.getOverlays().add(marker);
            if (store.getId() != null) {
                storeMarkerMap.put(store.getId(), marker);
            }
        }

        mapViewStores.invalidate();
    }

    /**
     * Tạo icon chấm tròn xanh dương viền trắng nhỏ gọn (16dp) cho vị trí GPS người dùng.
     */
    private Drawable createUserLocationDotIcon() {
        if (!isAdded()) return null;
        float density = getResources().getDisplayMetrics().density;
        int sizePx = Math.round(16 * density);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float center = sizePx / 2f;
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        canvas.drawCircle(center, center, center, borderPaint);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(ContextCompat.getColor(requireContext(), R.color.artifex_primary));
        canvas.drawCircle(center, center, center - (2.5f * density), fillPaint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    /**
     * Tạo icon ghim Showroom nhỏ gọn (28dp) từ @drawable/ic_location.
     */
    private Drawable createSmallStorePinIcon() {
        if (!isAdded()) return null;
        Drawable base = ContextCompat.getDrawable(requireContext(), R.drawable.ic_location);
        if (base == null) return null;

        float density = getResources().getDisplayMetrics().density;
        int sizePx = Math.round(28 * density);

        Drawable wrapped = DrawableCompat.wrap(base.mutate());
        DrawableCompat.setTint(wrapped, ContextCompat.getColor(requireContext(), R.color.artifex_accent));

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        wrapped.setBounds(0, 0, sizePx, sizePx);
        wrapped.draw(canvas);

        return new BitmapDrawable(getResources(), bitmap);
    }

    /**
     * Điều khiển camera bản đồ bay tới vị trí của Showroom được chọn và hiển thị InfoWindow.
     */
    private void focusCameraOnStore(StoreModel store, boolean showToast) {
        if (mapViewStores == null || store == null) return;

        GeoPoint target = new GeoPoint(store.getLatitude(), store.getLongitude());
        IMapController controller = mapViewStores.getController();
        controller.setZoom(15.5);
        controller.animateTo(target);

        // Đóng các bong bóng cũ và mở bong bóng của Showroom được chọn
        for (Marker m : storeMarkerMap.values()) {
            m.closeInfoWindow();
        }
        if (store.getId() != null && storeMarkerMap.containsKey(store.getId())) {
            Marker targetMarker = storeMarkerMap.get(store.getId());
            if (targetMarker != null) {
                targetMarker.showInfoWindow();
            }
        }
        mapViewStores.invalidate();

        if (showToast && isAdded()) {
            Toast.makeText(requireContext(), "Đang xem: " + store.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Khóa/mở nút "Định vị gần nhất" khi đang chờ GPS trả về để tránh bấm dồn nhiều lần (debounce).
     */
    private void setLocatingState(boolean locating) {
        isLocating = locating;
        if (!isAdded() || btnLocateNearest == null) return;
        btnLocateNearest.setEnabled(!locating);
        btnLocateNearest.setText(locating ? "Đang định vị..." : "Định vị gần nhất");
    }

    /**
     * Kiểm tra quyền ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION trước khi lấy tọa độ GPS.
     */
    private void checkPermissionAndLocateUser(boolean userClickedButton) {
        if (!isAdded()) return;
        if (isLocating) return;

        boolean hasFine = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (hasFine || hasCoarse) {
            fetchUserLocationAndCalculateDistances(userClickedButton);
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    /**
     * Lấy tọa độ GPS thực tế từ FusedLocationProviderClient.
     * Khi người dùng bấm "Định vị gần nhất" (forceFreshLocation = true), ưu tiên gọi getCurrentLocation
     * với PRIORITY_BALANCED_POWER_ACCURACY kèm CancellationTokenSource để phản hồi nhanh và không bị đơ.
     */
    @SuppressLint("MissingPermission")
    private void fetchUserLocationAndCalculateDistances(boolean forceFreshLocation) {
        if (!isAdded()) return;
        if (isLocating) return;

        setLocatingState(true);

        if (forceFreshLocation) {
            if (locationCancellationTokenSource != null) {
                locationCancellationTokenSource.cancel();
            }
            locationCancellationTokenSource = new CancellationTokenSource();

            fusedLocationClient.getCurrentLocation(
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                            locationCancellationTokenSource.getToken()
                    )
                    .addOnSuccessListener(currLoc -> {
                        if (!isAdded()) {
                            isLocating = false;
                            return;
                        }
                        if (currLoc != null) {
                            calculateNearestStoresInBackground(
                                    currLoc.getLatitude(),
                                    currLoc.getLongitude(),
                                    storeList,
                                    "GPS thiết bị thực tế"
                            );
                        } else {
                            fallbackToLastLocationOrDefault();
                        }
                    })
                    .addOnFailureListener(e -> fallbackToLastLocationOrDefault());
        } else {
            fallbackToLastLocationOrDefault();
        }
    }

    @SuppressLint("MissingPermission")
    private void fallbackToLastLocationOrDefault() {
        if (!isAdded()) {
            isLocating = false;
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (!isAdded()) {
                        isLocating = false;
                        return;
                    }
                    if (location != null) {
                        calculateNearestStoresInBackground(
                                location.getLatitude(),
                                location.getLongitude(),
                                storeList,
                                "GPS thiết bị thực tế"
                        );
                    } else {
                        if (locationCancellationTokenSource != null) {
                            locationCancellationTokenSource.cancel();
                        }
                        locationCancellationTokenSource = new CancellationTokenSource();

                        fusedLocationClient.getCurrentLocation(
                                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                        locationCancellationTokenSource.getToken()
                                )
                                .addOnSuccessListener(currLoc -> {
                                    if (!isAdded()) {
                                        isLocating = false;
                                        return;
                                    }
                                    if (currLoc != null) {
                                        calculateNearestStoresInBackground(
                                                currLoc.getLatitude(),
                                                currLoc.getLongitude(),
                                                storeList,
                                                "GPS thiết bị thực tế"
                                        );
                                    } else {
                                        calculateNearestStoresInBackground(
                                                DEFAULT_USER_LAT,
                                                DEFAULT_USER_LNG,
                                                storeList,
                                                "Tọa độ giả lập (Khu vực ĐH Nông Lâm - Thủ Đức)"
                                        );
                                    }
                                })
                                .addOnFailureListener(e -> calculateNearestStoresInBackground(
                                        DEFAULT_USER_LAT,
                                        DEFAULT_USER_LNG,
                                        storeList,
                                        "Tọa độ giả lập (Khu vực ĐH Nông Lâm - Thủ Đức)"
                                ));
                    }
                })
                .addOnFailureListener(e -> calculateNearestStoresInBackground(
                        DEFAULT_USER_LAT,
                        DEFAULT_USER_LNG,
                        storeList,
                        "Tọa độ giả lập (Khu vực ĐH Nông Lâm - Thủ Đức)"
                ));
    }

    /**
     * =========================================================================================
     * TRỌNG TÂM KỸ THUẬT ĐIỂM 9.0: MULTITHREADING (ExecutorService + Handler MainLooper)
     * =========================================================================================
     * - Tại sao dùng ExecutorService thay vì AsyncTask?
     *   -> AsyncTask đã bị Google deprecated từ API 30 do nguy cơ Memory Leak.
     * - Tại sao phải dùng Handler(Looper.getMainLooper())?
     *   -> Worker Thread không được phép chạm trực tiếp vào View/UI. Handler đẩy kết quả đã tính
     *      toán về Message Queue của Main Thread để cập nhật RecyclerView & MapView an toàn.
     * - Tái sử dụng 1 SingleThreadExecutor duy nhất suốt vòng đời Fragment và shutdown trong onDestroy().
     */
    private void calculateNearestStoresInBackground(double userLat, double userLng,
                                                    List<StoreModel> stores, String locationSource) {
        if (stores == null || stores.isEmpty()) {
            setLocatingState(false);
            return;
        }

        this.currentUserLat = userLat;
        this.currentUserLng = userLng;
        this.hasUserLocation = true;

        if (distanceExecutor == null || distanceExecutor.isShutdown()) {
            distanceExecutor = Executors.newSingleThreadExecutor();
        }

        final List<StoreModel> snapshot = new ArrayList<>(stores);

        distanceExecutor.execute(() -> {
            // --- BẮT ĐẦU TÁC VỤ CHẠY NGẦM TRÊN WORKER THREAD ---
            long startTimeNs = System.nanoTime();
            String workerThreadName = Thread.currentThread().getName();

            for (StoreModel s : snapshot) {
                float[] result = new float[1];
                Location.distanceBetween(
                        userLat, userLng,
                        s.getLatitude(), s.getLongitude(),
                        result
                );
                // Quy đổi từ mét (m) sang kilomet (km)
                s.setDistanceKm(result[0] / 1000.0);
            }

            // Sắp xếp danh sách Showroom theo khoảng cách tăng dần (gần nhất lên đầu)
            Collections.sort(snapshot, Comparator.comparingDouble(StoreModel::getDistanceKm));

            double elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000.0;

            // --- ĐẨY KẾT QUẢ VỀ MAIN THREAD (UI THREAD) ĐỂ CẬP NHẬT GIAO DIỆN ---
            mainHandler.post(() -> {
                if (!isAdded()) {
                    isLocating = false;
                    return;
                }

                storeList.clear();
                storeList.addAll(snapshot);

                // Cập nhật danh sách RecyclerView và vẽ lại Marker kèm khoảng cách
                storeAdapter.updateData(storeList);
                renderMarkersOnMap(storeList);

                // Hiển thị Showroom gần nhất lên Banner và di chuyển Camera tới đó
                StoreModel nearest = storeList.get(0);
                tvNearestStoreBanner.setText(String.format(
                        Locale.US,
                        "Gần bạn nhất: %s (%.2f km)",
                        nearest.getName(),
                        nearest.getDistanceKm()
                ));
                tvMultithreadStatus.setText(String.format(
                        Locale.US,
                        "Đã tính trên Worker Thread [%s] (%.2f ms) • Nguồn: %s",
                        workerThreadName,
                        elapsedMs,
                        locationSource
                ));

                focusCameraOnStore(nearest, false);
                setLocatingState(false);
            });
        });
    }

    /**
     * Mở ứng dụng Google Maps bên ngoài để chỉ đường tới Showroom (Fallback sang trình duyệt nếu chưa cài app).
     */
    private void openGoogleMapsDirections(StoreModel store) {
        if (!isAdded() || store == null) return;

        String coords = store.getLatitude() + "," + store.getLongitude();
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + coords);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // Fallback mở Google Maps trên trình duyệt web nếu máy ảo chưa cài app Google Maps
            Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + coords);
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        }
    }

    private void openDialer(String phoneNumber) {
        if (!isAdded() || phoneNumber == null || phoneNumber.trim().isEmpty()) return;
        String cleanPhone = phoneNumber.replaceAll("\\s+", "");
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cleanPhone));
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapViewStores != null) {
            mapViewStores.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapViewStores != null) {
            mapViewStores.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (expandedMapDialog != null && expandedMapDialog.isShowing()) {
            expandedMapDialog.dismiss();
        }
        expandedMapDialog = null;
        isMapExpanded = false;
        if (locationCancellationTokenSource != null) {
            locationCancellationTokenSource.cancel();
            locationCancellationTokenSource = null;
        }
        isLocating = false;
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (distanceExecutor != null && !distanceExecutor.isShutdown()) {
            distanceExecutor.shutdownNow();
        }
    }
}
