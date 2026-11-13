package com.artifex.app.ui.auth;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.EditText;
import org.json.JSONObject;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artifex.app.MainActivity;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.ArrayList;
import com.artifex.app.R;
import com.artifex.app.api.ApiService;
import com.artifex.app.api.RetrofitClient;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthFragment extends Fragment {

    private LinearLayout layoutAuthForm, layoutProfile;
    private TextInputLayout tilFullName, tilEmail, tilPhone, tilPassword, tilConfirmPassword;
    private TextInputEditText etFullName, etEmail, etPhone, etPassword, etConfirmPassword;
    private Button btnSubmitAuth, btnFirebaseAuth;
    private View btnLogout, btnChangePassword, cardShippingInfo;
    private View btnViewAllOrders, btnOrderPending, btnOrderProcessing, btnOrderShipping, btnOrderCompleted;
    private TextView tvAuthTitle, tvToggleMode, tvForgotPassword;
    private ProgressBar progressBar;

    private ImageView ivAvatar;
    private ImageButton btnChangeAvatar;
    private ImageView btnEditPersonalInfo;
    private TextView tvProfileFullName, tvProfileEmail;
    private View cardPhoneWarning, btnUpdatePhoneWarning;

    // Ô duy nhất hiển thị địa chỉ giao hàng được chọn
    private View cardSelectedAddress, layoutEmptyAddress, btnEmptyAddAddress;
    private View btnAddNewAddressMain;
    private TextView tvSelectedReceiverName, tvSelectedTag, tvSelectedDefaultBadge, tvSelectedPhone, tvSelectedAddressLine;

    private boolean isRegisterMode = false;
    private ApiService apiService;
    private SharedPrefManager prefManager;
    private FirebaseAuth mAuth;

    // Recommendations (Có thể bạn cũng thích - Độc lập trong ui/auth)
    private RecyclerView rvRecommendations;
    private ProgressBar progressRecommendations;
    private RecommendedProductAdapter recommendedProductAdapter;

    private CountDownTimer registerOtpTimer;
    private CountDownTimer forgotOtpTimer;
    private AlertDialog registerOtpDialog;
    private AlertDialog forgotPasswordDialog;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) {
                    uploadAvatarToCloudinary(uri);
                }
            }
    );

    public AuthFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_auth, container, false);
        initViews(view);
        setupEvents();
        checkLoginState();
        return view;
    }

    private void initViews(View v) {
        apiService = RetrofitClient.getApiService();
        prefManager = SharedPrefManager.getInstance(requireContext());
        mAuth = FirebaseAuthHelper.getFirebaseAuth(requireContext());

        layoutAuthForm = v.findViewById(R.id.layoutAuthForm);
        layoutProfile = v.findViewById(R.id.layoutProfile);
        tilFullName = v.findViewById(R.id.tilFullName);
        tilEmail = v.findViewById(R.id.tilEmail);
        tilPhone = v.findViewById(R.id.tilPhone);
        tilPassword = v.findViewById(R.id.tilPassword);
        tilConfirmPassword = v.findViewById(R.id.tilConfirmPassword);
        etFullName = v.findViewById(R.id.etFullName);
        etEmail = v.findViewById(R.id.etEmail);
        etPhone = v.findViewById(R.id.etPhone);
        etPassword = v.findViewById(R.id.etPassword);
        etConfirmPassword = v.findViewById(R.id.etConfirmPassword);

        btnSubmitAuth = v.findViewById(R.id.btnSubmitAuth);
        btnFirebaseAuth = v.findViewById(R.id.btnFirebaseAuth);
        btnChangePassword = v.findViewById(R.id.btnChangePassword);
        btnLogout = v.findViewById(R.id.btnLogout);
        cardShippingInfo = v.findViewById(R.id.cardShippingInfo);

        btnViewAllOrders = v.findViewById(R.id.btnViewAllOrders);
        btnOrderPending = v.findViewById(R.id.btnOrderPending);
        btnOrderProcessing = v.findViewById(R.id.btnOrderProcessing);
        btnOrderShipping = v.findViewById(R.id.btnOrderShipping);
        btnOrderCompleted = v.findViewById(R.id.btnOrderCompleted);

        tvAuthTitle = v.findViewById(R.id.tvAuthTitle);
        tvToggleMode = v.findViewById(R.id.tvToggleMode);
        tvForgotPassword = v.findViewById(R.id.tvForgotPassword);
        progressBar = v.findViewById(R.id.progressBar);

        ivAvatar = v.findViewById(R.id.ivAvatar);
        btnChangeAvatar = v.findViewById(R.id.btnChangeAvatar);
        btnEditPersonalInfo = v.findViewById(R.id.btnEditPersonalInfo);
        tvProfileFullName = v.findViewById(R.id.tvProfileFullName);
        tvProfileEmail = v.findViewById(R.id.tvProfileEmail);
        cardPhoneWarning = v.findViewById(R.id.cardPhoneWarning);
        btnUpdatePhoneWarning = v.findViewById(R.id.btnUpdatePhoneWarning);

        // Ô duy nhất hiển thị địa chỉ giao hàng
        btnAddNewAddressMain = v.findViewById(R.id.btnAddNewAddressMain);
        cardSelectedAddress = v.findViewById(R.id.cardSelectedAddress);
        layoutEmptyAddress = v.findViewById(R.id.layoutEmptyAddress);
        btnEmptyAddAddress = v.findViewById(R.id.btnEmptyAddAddress);

        tvSelectedReceiverName = v.findViewById(R.id.tvSelectedReceiverName);
        tvSelectedTag = v.findViewById(R.id.tvSelectedTag);
        tvSelectedDefaultBadge = v.findViewById(R.id.tvSelectedDefaultBadge);
        tvSelectedPhone = v.findViewById(R.id.tvSelectedPhone);
        tvSelectedAddressLine = v.findViewById(R.id.tvSelectedAddressLine);

        // Khởi tạo phần Gợi ý "Có thể bạn cũng thích"
        rvRecommendations = v.findViewById(R.id.rvRecommendations);
        progressRecommendations = v.findViewById(R.id.progressRecommendations);
        if (rvRecommendations != null) {
            rvRecommendations.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            rvRecommendations.setNestedScrollingEnabled(false);
            recommendedProductAdapter = new RecommendedProductAdapter(new ArrayList<>(), this::showProductDetailBottomSheet);
            rvRecommendations.setAdapter(recommendedProductAdapter);
        }
    }

    private void setupEvents() {
        tvToggleMode.setOnClickListener(v -> toggleAuthMode());

        if (etEmail != null) {
            etEmail.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!isRegisterMode) return;
                    // Chỉ kiểm tra sau khi gõ xong (rời ô hoặc bấm xong); xóa lỗi ngay khi đang sửa.
                    clearEmailError();
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });

            etEmail.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    validateRegisterEmailOnComplete();
                }
            });

            etEmail.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
                        || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    validateRegisterEmailOnComplete();
                }
                return false;
            });
        }

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }

        btnSubmitAuth.setOnClickListener(v -> {
            if (isRegisterMode) {
                handleRegister();
            } else {
                handleLogin();
            }
        });

        btnFirebaseAuth.setOnClickListener(v -> handleFirebaseLogin());

        btnChangeAvatar.setOnClickListener(v -> {
            photoPickerLauncher.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build()
            );
        });

        if (btnUpdatePhoneWarning != null) {
            btnUpdatePhoneWarning.setOnClickListener(v -> showEditPersonalInfoDialog());
        }

        // Bấm nút sửa thông tin cá nhân ở trên đầu
        if (btnEditPersonalInfo != null) {
            btnEditPersonalInfo.setOnClickListener(v -> showEditPersonalInfoDialog());
        }

        // Chạm vào ô địa chỉ duy nhất để mở danh sách các địa chỉ đã lưu và chọn/sửa/xóa địa chỉ
        if (cardSelectedAddress != null) {
            cardSelectedAddress.setOnClickListener(v -> showAddressBookBottomSheet());
        }

        // Bấm nút + Thêm địa chỉ (ở cả header lẫn khi chưa có địa chỉ nào)
        View.OnClickListener addAddressClick = v -> showAddAddressDialog();
        if (btnAddNewAddressMain != null) btnAddNewAddressMain.setOnClickListener(addAddressClick);
        if (btnEmptyAddAddress != null) btnEmptyAddAddress.setOnClickListener(addAddressClick);

        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        if (btnViewAllOrders != null) {
            btnViewAllOrders.setOnClickListener(v ->
                    com.artifex.app.ui.store.OrderHistoryActivity.start(requireContext(), com.artifex.app.ui.store.StoreFragment.FILTER_ALL));
        }
        if (btnOrderPending != null) {
            btnOrderPending.setOnClickListener(v ->
                    com.artifex.app.ui.store.OrderHistoryActivity.start(requireContext(), com.artifex.app.ui.store.StoreFragment.FILTER_PENDING));
        }
        if (btnOrderProcessing != null) {
            btnOrderProcessing.setOnClickListener(v ->
                    com.artifex.app.ui.store.OrderHistoryActivity.start(requireContext(), com.artifex.app.ui.store.StoreFragment.FILTER_PENDING));
        }
        if (btnOrderShipping != null) {
            btnOrderShipping.setOnClickListener(v ->
                    com.artifex.app.ui.store.OrderHistoryActivity.start(requireContext(), com.artifex.app.ui.store.StoreFragment.FILTER_SHIPPING));
        }
        if (btnOrderCompleted != null) {
            btnOrderCompleted.setOnClickListener(v ->
                    com.artifex.app.ui.store.OrderHistoryActivity.start(requireContext(), com.artifex.app.ui.store.StoreFragment.FILTER_COMPLETED));
        }

        btnLogout.setOnClickListener(v -> showConfirmLogoutDialog());
    }

    private void showConfirmLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản hiện tại không?")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    if (!isAdded()) return;
                    String token = prefManager.getAuthToken();
                    if (token != null && !token.trim().isEmpty()) {
                        String authHeader = "Bearer " + token.trim();
                        apiService.logout(authHeader).enqueue(new Callback<Map<String, Object>>() {
                            @Override
                            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}

                            @Override
                            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                        });
                    }
                    prefManager.clear();
                    signOutFirebaseClient(true);
                    clearAuthFormInputs();
                    resetAvatarPlaceholder();
                    checkLoginState();
                    Toast.makeText(requireContext(), "Đã đăng xuất!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void toggleAuthMode() {
        isRegisterMode = !isRegisterMode;
        if (isRegisterMode) {
            tilFullName.setVisibility(View.VISIBLE);
            if (tilEmail != null) {
                tilEmail.setVisibility(View.VISIBLE);
                clearEmailError();
            }
            if (tilPhone != null) {
                tilPhone.setVisibility(View.VISIBLE);
            }
            if (tilConfirmPassword != null) {
                tilConfirmPassword.setVisibility(View.VISIBLE);
            }
            if (tvForgotPassword != null) {
                tvForgotPassword.setVisibility(View.GONE);
            }
            btnSubmitAuth.setText("Tạo Tài Khoản Mới");
            tvAuthTitle.setText("Đăng ký tài khoản Artifex");
            tvToggleMode.setText("Đã có tài khoản? Đăng nhập ngay");
        } else {
            tilFullName.setVisibility(View.GONE);
            if (tilEmail != null) {
                tilEmail.setVisibility(View.GONE);
                clearEmailError();
            }
            if (tilPhone != null) {
                tilPhone.setVisibility(View.VISIBLE);
            }
            if (tilConfirmPassword != null) {
                tilConfirmPassword.setVisibility(View.GONE);
            }
            if (tvForgotPassword != null) {
                tvForgotPassword.setVisibility(View.VISIBLE);
            }
            btnSubmitAuth.setText("Đăng Nhập");
            tvAuthTitle.setText("Chào mừng bạn đến với Artifex PC");
            tvToggleMode.setText("Chưa có tài khoản? Đăng ký ngay");
        }
    }

            private void checkLoginState() {
        if (prefManager.isLoggedIn()) {
            layoutAuthForm.setVisibility(View.GONE);
            layoutProfile.setVisibility(View.VISIBLE);
            bindProfileData();
            fetchLatestUserProfile();
            loadRecommendationProducts();
        } else {
            layoutAuthForm.setVisibility(View.VISIBLE);
            layoutProfile.setVisibility(View.GONE);
        }
    }

    private void fetchLatestUserProfile() {
        Long userId = prefManager.getUserId();
        if (userId == null || userId <= 0) return;

        apiService.getUserProfile(userId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                if (response.code() == 401) {
                    prefManager.clear();
                    signOutFirebaseClient(false);
                    clearAuthFormInputs();
                    resetAvatarPlaceholder();
                    checkLoginState();
                    Toast.makeText(requireContext(), "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại!", Toast.LENGTH_LONG).show();
                    return;
                }
                if (response.isSuccessful() && applyAuthSessionFromResponse(response.body(), false)) {
                    bindProfileData();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // Giữ nguyên dữ liệu cache trong SharedPrefManager nếu offline
            }
        });
    }

    private void refreshAddressList() {
        List<UserAddress> currentList = prefManager.getAddressList();

        if (currentList == null || currentList.isEmpty()) {
            if (layoutEmptyAddress != null) layoutEmptyAddress.setVisibility(View.VISIBLE);
            if (cardSelectedAddress != null) cardSelectedAddress.setVisibility(View.GONE);
            return;
        }

        if (layoutEmptyAddress != null) layoutEmptyAddress.setVisibility(View.GONE);
        if (cardSelectedAddress != null) cardSelectedAddress.setVisibility(View.VISIBLE);

        // Hiển thị 1 ô duy nhất: Địa chỉ được chọn (mặc định)
        UserAddress selectedAddr = currentList.get(0);
        for (UserAddress a : currentList) {
            if (a.isDefault()) {
                selectedAddr = a;
                break;
            }
        }

        if (tvSelectedReceiverName != null) tvSelectedReceiverName.setText(selectedAddr.getReceiverName());
        if (tvSelectedTag != null) tvSelectedTag.setText(selectedAddr.getTag());
        if (tvSelectedPhone != null) tvSelectedPhone.setText(selectedAddr.getPhone());
        if (tvSelectedAddressLine != null) tvSelectedAddressLine.setText(selectedAddr.getAddressLine());
        if (tvSelectedDefaultBadge != null) {
            tvSelectedDefaultBadge.setVisibility(selectedAddr.isDefault() ? View.VISIBLE : View.GONE);
        }
    }

    private void bindProfileData() {
        tvProfileFullName.setText(prefManager.getFullName());
        tvProfileEmail.setText(prefManager.getEmail());

        // Hiển thị cảnh báo nếu tài khoản (ví dụ đăng nhập Google) chưa có số điện thoại
        String phone = prefManager.getPhone();
        if (cardPhoneWarning != null) {
            cardPhoneWarning.setVisibility((phone == null || phone.trim().isEmpty()) ? View.VISIBLE : View.GONE);
        }

        // Cập nhật danh sách địa chỉ giao hàng hiển thị trực tiếp
        refreshAddressList();

        if (ivAvatar != null) {
            String avatarUrl = prefManager.getAvatarUrl();
            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                Glide.with(this)
                        .load(avatarUrl.trim())
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .circleCrop()
                        .into(ivAvatar);
            } else {
                resetAvatarPlaceholder();
            }
        }
    }

    // ================= CHỈNH SỬA THÔNG TIN CÁ NHÂN (HỌ TÊN, EMAIL, SĐT CÁ NHÂN, NGÀY SINH, GIỚI TÍNH) =================
    private void showEditPersonalInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_personal_info, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextInputEditText etEditFullName = dialogView.findViewById(R.id.etEditFullName);
        TextInputEditText etEditEmail = dialogView.findViewById(R.id.etEditEmail);
        TextInputEditText etEditPhone = dialogView.findViewById(R.id.etEditPersonalPhone);
        TextInputEditText etEditDob = dialogView.findViewById(R.id.etEditDob);
        RadioGroup rgGender = dialogView.findViewById(R.id.rgGender);
        RadioButton rbGenderMale = dialogView.findViewById(R.id.rbGenderMale);
        RadioButton rbGenderFemale = dialogView.findViewById(R.id.rbGenderFemale);
        RadioButton rbGenderOther = dialogView.findViewById(R.id.rbGenderOther);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelEditPersonal);
        Button btnSave = dialogView.findViewById(R.id.btnSaveEditPersonal);

        etEditFullName.setText(prefManager.getFullName());
        etEditEmail.setText(prefManager.getEmail());
        etEditPhone.setText(prefManager.getPhone());
        etEditDob.setText(prefManager.getDob());

        String currentGender = prefManager.getGender();
        if ("Nữ".equalsIgnoreCase(currentGender)) {
            rbGenderFemale.setChecked(true);
        } else if ("Khác".equalsIgnoreCase(currentGender)) {
            rbGenderOther.setChecked(true);
        } else {
            rbGenderMale.setChecked(true);
        }

        // Bấm vào ô ngày sinh sẽ mở DatePickerDialog chuẩn
        View.OnClickListener datePickerListener = v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR) - 20;
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            String currentDob = etEditDob.getText() != null ? etEditDob.getText().toString().trim() : "";
            if (!currentDob.isEmpty() && currentDob.contains("/")) {
                String[] parts = currentDob.split("/");
                if (parts.length == 3) {
                    try {
                        day = Integer.parseInt(parts[0]);
                        month = Integer.parseInt(parts[1]) - 1;
                        year = Integer.parseInt(parts[2]);
                    } catch (Exception ignored) {}
                }
            }

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        String formatted = String.format(Locale.getDefault(), "%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear);
                        etEditDob.setText(formatted);
                    },
                    year, month, day
            );
            datePickerDialog.show();
        };

        etEditDob.setOnClickListener(datePickerListener);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newFullName = etEditFullName.getText() != null ? etEditFullName.getText().toString().trim() : "";
            String newEmail = etEditEmail.getText() != null ? etEditEmail.getText().toString().trim() : "";
            String newPhone = etEditPhone.getText() != null ? etEditPhone.getText().toString().trim() : "";
            String newDob = etEditDob.getText() != null ? etEditDob.getText().toString().trim() : "";

            String selectedGender = "Nam";
            int checkedId = rgGender.getCheckedRadioButtonId();
            if (checkedId == R.id.rbGenderFemale) {
                selectedGender = "Nữ";
            } else if (checkedId == R.id.rbGenderOther) {
                selectedGender = "Khác";
            }

            if (newFullName.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập họ và tên!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidProfileEmail(newEmail, prefManager.getEmail())) {
                Toast.makeText(requireContext(), "Email không hợp lệ (hỗ trợ @gmail.com hoặc @artifex.vn)!", Toast.LENGTH_SHORT).show();
                return;
            }

            String cleanPhone = normalizeLoginPhone(newPhone);
            if (cleanPhone.isEmpty()) {
                Toast.makeText(requireContext(), "Số điện thoại không hợp lệ! Vui lòng nhập đúng 10 số (ví dụ: 09xxxxxxxx).", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            btnSave.setEnabled(false);

            // Đồng bộ lên MySQL backend
            Map<String, Object> body = new HashMap<>();
            body.put("fullName", newFullName);
            body.put("email", newEmail);
            body.put("phone", cleanPhone);
            body.put("dateOfBirth", newDob);
            body.put("gender", selectedGender);

            final String finalGender = selectedGender;
            apiService.updateProfile(prefManager.getUserId(), body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);

                    if (response.isSuccessful()) {
                        prefManager.updatePersonalInfo(newFullName, newEmail, cleanPhone, newDob, finalGender);
                        bindProfileData();
                        dialog.dismiss();
                        Toast.makeText(requireContext(), "Cập nhật thông tin cá nhân thành công!", Toast.LENGTH_SHORT).show();
                    } else {
                        String errorMsg = extractErrorMessage(response, "Cập nhật thất bại!");
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void handleLogin() {
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        if (normalizeLoginPhone(phone).isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập số điện thoại hợp lệ và mật khẩu", Toast.LENGTH_SHORT).show();
            return;
        }
        if (containsWhitespace(password)) {
            Toast.makeText(requireContext(), "Mật khẩu không được chứa khoảng trắng!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSubmitAuth.setEnabled(false);
        Map<String, Object> body = new HashMap<>();
        body.put("phone", normalizeLoginPhone(phone));
        body.put("password", password);

        apiService.login(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded() || getView() == null) return;
                progressBar.setVisibility(View.GONE);
                btnSubmitAuth.setEnabled(true);
                if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                    if (!applyAuthSessionFromResponse(response.body(), false)) {
                        Toast.makeText(requireContext(), "Phản hồi tài khoản không hợp lệ!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    clearAuthFormInputs();
                    checkLoginState();
                    Toast.makeText(requireContext(), "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                } else {
                    String errMsg = extractErrorMessage(response, "Sai thông tin đăng nhập!");
                    Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (!isAdded() || getView() == null) return;
                progressBar.setVisibility(View.GONE);
                btnSubmitAuth.setEnabled(true);
                Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String normalizeLoginPhone(String rawPhone) {
        String phone = rawPhone.trim().replaceAll("[\\s.()\\-]", "");
        if (phone.startsWith("+84")) phone = "0" + phone.substring(3);
        else if (phone.startsWith("84")) phone = "0" + phone.substring(2);
        return phone.matches("0[35789][0-9]{8}") ? phone : "";
    }

    private boolean isValidGmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim().toLowerCase();
        return trimmed.matches("^[a-zA-Z0-9._%+-]+@gmail\\.com$");
    }

    private void clearEmailError() {
        if (tilEmail == null) return;
        tilEmail.setError(null);
        tilEmail.setErrorEnabled(false);
    }

    private void validateRegisterEmailOnComplete() {
        if (!isAdded() || !isRegisterMode || tilEmail == null || etEmail == null) return;
        String input = etEmail.getText() != null ? etEmail.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        if (input.isEmpty() || isValidGmail(input)) {
            clearEmailError();
        } else {
            tilEmail.setErrorEnabled(true);
            tilEmail.setError("Email phải có định dạng @gmail.com");
        }
    }

    private boolean isValidProfileEmail(String email, String existingEmail) {
        if (email == null || email.trim().isEmpty()) return false;
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        if (existingEmail != null && trimmed.equals(existingEmail.trim().toLowerCase(Locale.ROOT))
                && Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            return true;
        }
        return trimmed.matches("^[a-zA-Z0-9._%+-]+@(gmail\\.com|artifex\\.vn)$");
    }

    private boolean containsWhitespace(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void handleRegister() {
        String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim().toLowerCase() : "";
        String phone = (etPhone != null && etPhone.getText() != null) ? etPhone.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmPassword = (etConfirmPassword != null && etConfirmPassword.getText() != null)
                ? etConfirmPassword.getText().toString() : "";

        if (fullName.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ tất cả thông tin!", Toast.LENGTH_SHORT).show();
            if (email.isEmpty() && tilEmail != null) {
                tilEmail.setErrorEnabled(true);
                tilEmail.setError("Vui lòng nhập địa chỉ Gmail!");
            } else {
                validateRegisterEmailOnComplete();
            }
            return;
        }

        if (!isValidGmail(email)) {
            if (tilEmail != null) {
                tilEmail.setErrorEnabled(true);
                tilEmail.setError("Email phải có định dạng @gmail.com (ví dụ: name@gmail.com)");
            }
            Toast.makeText(requireContext(), "Email phải có định dạng @gmail.com!", Toast.LENGTH_SHORT).show();
            etEmail.requestFocus();
            return;
        } else {
            clearEmailError();
        }

        String cleanPhone = normalizeLoginPhone(phone);
        if (cleanPhone.length() != 10 || !cleanPhone.startsWith("0")) {
            Toast.makeText(requireContext(), "Số điện thoại không hợp lệ! Vui lòng nhập đúng 10 số (ví dụ: 09xxxxxxxx) để nhận SMS OTP.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (containsWhitespace(password) || containsWhitespace(confirmPassword)) {
            Toast.makeText(requireContext(), "Mật khẩu không được chứa khoảng trắng!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(requireContext(), "Mật khẩu phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(requireContext(), "Mật khẩu xác nhận không trùng khớp!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Bước 1: Gửi mã OTP xác thực tới số điện thoại người dùng
        progressBar.setVisibility(View.VISIBLE);
        btnSubmitAuth.setEnabled(false);

        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("phone", cleanPhone);
        reqBody.put("email", email);

        apiService.sendRegisterOtp(reqBody).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                btnSubmitAuth.setEnabled(true);

                if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                    String msg = (String) response.body().get("message");
                    Toast.makeText(requireContext(), msg != null ? msg : "Đã gửi mã xác thực OTP qua tin nhắn SMS!", Toast.LENGTH_LONG).show();
                    showRegisterOtpDialog(fullName, email, cleanPhone, password, 180000L);
                } else if (response.code() == 429) {
                    // Backend: Mã OTP đã gửi và vẫn còn hạn -> Tự động mở lại Dialog OTP để người dùng nhập mã, không bị kẹt!
                    String errorMsg = extractErrorMessage(response, "Mã OTP đã được gửi và vẫn còn hiệu lực trong 3 phút.");
                    Toast.makeText(requireContext(), errorMsg + " Mở lại giao diện xác thực OTP...", Toast.LENGTH_SHORT).show();
                    showRegisterOtpDialog(fullName, email, cleanPhone, password, extractOtpCooldownMillis(errorMsg));
                } else {
                    String errorMsg = extractErrorMessage(response, "Gửi mã OTP thất bại!");
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                btnSubmitAuth.setEnabled(true);
                Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRegisterOtpDialog(
            String fullName,
            String email,
            String phone,
            String password,
            long initialCountdownMillis) {

        cancelRegisterOtpTimer();
        if (registerOtpDialog != null && registerOtpDialog.isShowing()) {
            registerOtpDialog.dismiss();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_register_otp, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        registerOtpDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setOnDismissListener(d -> {
            cancelRegisterOtpTimer();
            if (registerOtpDialog == dialog) {
                registerOtpDialog = null;
            }
        });

        TextView tvTargetPhone = dialogView.findViewById(R.id.tvRegisterTargetPhone);
        EditText etOtp = dialogView.findViewById(R.id.etRegisterOtp);
        TextView tvResendCountdown = dialogView.findViewById(R.id.tvRegisterResendCountdown);
        TextView btnResendOtp = dialogView.findViewById(R.id.btnRegisterResendOtp);
        Button btnConfirm = dialogView.findViewById(R.id.btnConfirmRegisterOtp);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelRegisterOtp);

        if (tvTargetPhone != null) {
            String masked = phone.length() >= 7
                    ? phone.substring(0, 3) + "***" + phone.substring(phone.length() - 3)
                    : phone;
            tvTargetPhone.setText(masked);
        }

        // Để ô nhập OTP trống hoàn toàn để người dùng tự xem SMS trên điện thoại rồi nhập vào
        if (etOtp != null) {
            etOtp.setText("");
            etOtp.requestFocus();
        }

        java.util.function.LongConsumer startRegisterCountdown = durationMs -> {
            cancelRegisterOtpTimer();
            registerOtpTimer = startOtpCountdown(
                    durationMs,
                    tvResendCountdown,
                    btnResendOtp,
                    () -> isAdded() && dialog.isShowing());
        };
        startRegisterCountdown.accept(initialCountdownMillis);

        if (btnResendOtp != null) {
            btnResendOtp.setOnClickListener(v -> {
                btnResendOtp.setEnabled(false);
                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("phone", phone);
                reqBody.put("email", email);

                apiService.sendRegisterOtp(reqBody).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded() || !dialog.isShowing()) return;
                        btnResendOtp.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                            Toast.makeText(requireContext(), "Đã gửi lại mã OTP mới qua SMS!", Toast.LENGTH_SHORT).show();
                            startRegisterCountdown.accept(180000L);
                        } else {
                            String err = extractErrorMessage(response, "Gửi lại mã thất bại!");
                            Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded() || !dialog.isShowing()) return;
                        btnResendOtp.setEnabled(true);
                        Toast.makeText(requireContext(), "Lỗi mạng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        btnConfirm.setOnClickListener(v -> {
            String otpCode = etOtp != null ? etOtp.getText().toString().trim() : "";
            if (otpCode.length() != 6) {
                Toast.makeText(requireContext(), "Vui lòng nhập đủ 6 chữ số OTP!", Toast.LENGTH_SHORT).show();
                return;
            }

            btnConfirm.setEnabled(false);
            btnConfirm.setText("Đang xác thực...");

            Map<String, Object> regBody = new HashMap<>();
            regBody.put("fullName", fullName);
            regBody.put("email", email);
            regBody.put("phone", phone);
            regBody.put("password", password);
            regBody.put("otp", otpCode);

            apiService.register(regBody).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Xác nhận & Tạo tài khoản");

                    if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                        if (!applyAuthSessionFromResponse(response.body(), false)) {
                            Toast.makeText(requireContext(), "Phản hồi tài khoản không hợp lệ!", Toast.LENGTH_LONG).show();
                            return;
                        }
                        cancelRegisterOtpTimer();
                        dialog.dismiss();
                        clearAuthFormInputs();
                        checkLoginState();
                        Toast.makeText(requireContext(), "Đăng ký tài khoản và xác thực số điện thoại thành công!", Toast.LENGTH_LONG).show();
                    } else {
                        String errMsg = extractErrorMessage(response, "Xác thực OTP thất bại!");
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Xác nhận & Tạo tài khoản");
                    Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnCancel.setOnClickListener(v -> {
            cancelRegisterOtpTimer();
            dialog.dismiss();
        });

        dialog.show();
    }

    private String extractErrorMessage(retrofit2.Response<?> response, String defaultMsg) {
        if (response != null) {
            if (response.code() == 404) {
                return "Máy chủ chưa cập nhật API mới! Vui lòng khởi động lại Backend (Spring Boot).";
            }
            if (response.body() instanceof Map) {
                Object msg = ((Map<?, ?>) response.body()).get("message");
                if (msg != null && !msg.toString().trim().isEmpty()) {
                    return msg.toString();
                }
            }
            if (response.errorBody() != null) {
                try {
                    String errorStr = response.errorBody().string();
                    JSONObject obj = new JSONObject(errorStr);
                    if (obj.has("message")) {
                        return obj.getString("message");
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return defaultMsg;
    }

    private android.os.CancellationSignal googleSignInCancellation;
    private Call<Map<String, Object>> firebaseLoginCall;
    private int firebaseAttempt;

    private void handleFirebaseLogin() {
        final int attempt = ++firebaseAttempt;
        progressBar.setVisibility(View.VISIBLE);
        btnFirebaseAuth.setEnabled(false);
        googleSignInCancellation = new android.os.CancellationSignal();
        FirebaseAuthHelper.signInWithGoogle(requireActivity(), googleSignInCancellation,
                new FirebaseAuthHelper.FirebaseAuthCallback() {
                    @Override
                    public void onSuccess(String idToken) {
                        if (!isCurrentFirebaseAttempt(attempt)) return;
                        syncFirebaseUserToBackend(idToken, attempt);
                    }

                    @Override
                    public void onError(String message) {
                        if (!isCurrentFirebaseAttempt(attempt)) return;
                        signOutFirebaseClient(false);
                        finishFirebaseLogin();
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean isCurrentFirebaseAttempt(int attempt) {
        return attempt == firebaseAttempt && isAdded() && getView() != null;
    }

    private void finishFirebaseLogin() {
        progressBar.setVisibility(View.GONE);
        btnFirebaseAuth.setEnabled(true);
    }

    private void syncFirebaseUserToBackend(String idToken, int attempt) {
        Map<String, Object> body = new HashMap<>();
        body.put("idToken", idToken);
        firebaseLoginCall = apiService.firebaseLogin(body);
        firebaseLoginCall.enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isCurrentFirebaseAttempt(attempt)) return;
                finishFirebaseLogin();
                if (response.isSuccessful() && response.body() != null
                        && "SUCCESS".equals(response.body().get("status"))) {
                    if (!applyAuthSessionFromResponse(response.body(), true)) {
                        signOutFirebaseClient(true);
                        Toast.makeText(requireContext(), "Phản hồi tài khoản không hợp lệ!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    clearAuthFormInputs();
                    checkLoginState();
                    Toast.makeText(requireContext(), "Đăng nhập Google thành công!", Toast.LENGTH_SHORT).show();
                } else {
                    signOutFirebaseClient(true);
                    Toast.makeText(requireContext(), extractErrorMessage(response,
                            "Không thể xác thực tài khoản Google với máy chủ!"), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (!isCurrentFirebaseAttempt(attempt) || call.isCanceled()) return;
                signOutFirebaseClient(false);
                finishFirebaseLogin();
                Toast.makeText(requireContext(), "Không thể kết nối máy chủ. Vui lòng thử lại.", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        firebaseAttempt++;
        if (googleSignInCancellation != null) googleSignInCancellation.cancel();
        if (firebaseLoginCall != null) firebaseLoginCall.cancel();
        cancelRegisterOtpTimer();
        cancelForgotOtpTimer();
        if (registerOtpDialog != null && registerOtpDialog.isShowing()) {
            registerOtpDialog.dismiss();
        }
        registerOtpDialog = null;
        if (forgotPasswordDialog != null && forgotPasswordDialog.isShowing()) {
            forgotPasswordDialog.dismiss();
        }
        forgotPasswordDialog = null;
        super.onDestroyView();
    }

    // ================= QUÊN MẬT KHẨU TÀI KHOẢN THƯỜNG (GỬI TIN NHẮN SMS OTP 6 SỐ QUA TEXTBEE) =================
    private void showForgotPasswordDialog() {
        cancelForgotOtpTimer();
        if (forgotPasswordDialog != null && forgotPasswordDialog.isShowing()) {
            forgotPasswordDialog.dismiss();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_forgot_password, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        forgotPasswordDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvSubtitle = dialogView.findViewById(R.id.tvForgotSubtitle);
        LinearLayout layoutStep1 = dialogView.findViewById(R.id.layoutForgotStep1);
        LinearLayout layoutStep2 = dialogView.findViewById(R.id.layoutForgotStep2);
        LinearLayout layoutStep3 = dialogView.findViewById(R.id.layoutForgotStep3);

        // Các View Bước 1
        TextInputEditText etForgotContact = dialogView.findViewById(R.id.etForgotContact);
        Button btnCancelStep1 = dialogView.findViewById(R.id.btnCancelForgotStep1);
        Button btnRequestOtp = dialogView.findViewById(R.id.btnRequestOtp);

        // Các View Bước 2 (Chỉ nhập & xác thực mã OTP)
        TextView tvOtpDeliveryStatus = dialogView.findViewById(R.id.tvOtpDeliveryStatus);
        TextInputEditText etForgotOtpCode = dialogView.findViewById(R.id.etForgotOtpCode);
        TextView tvForgotResendCountdown = dialogView.findViewById(R.id.tvForgotResendCountdown);
        TextView btnForgotResendOtp = dialogView.findViewById(R.id.btnForgotResendOtp);
        Button btnBackToStep1 = dialogView.findViewById(R.id.btnBackToStep1);
        Button btnVerifyForgotOtp = dialogView.findViewById(R.id.btnVerifyForgotOtp);

        // Các View Bước 3 (Màn hình nhập mật khẩu mới & nhập lại mật khẩu)
        TextInputEditText etNewPassword = dialogView.findViewById(R.id.etForgotNewPassword);
        TextInputEditText etConfirmPassword = dialogView.findViewById(R.id.etForgotConfirmPassword);
        Button btnBackToStep2 = dialogView.findViewById(R.id.btnBackToStep2);
        Button btnSubmitForgotPass = dialogView.findViewById(R.id.btnSubmitForgotPass);

        final String[] verifiedPhoneHolder = new String[]{""};
        final String[] verifiedOtpHolder = new String[]{""};

        java.util.function.IntConsumer showStep = step -> {
            layoutStep1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
            layoutStep2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
            layoutStep3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
            if (tvSubtitle == null) return;
            if (step == 1) {
                tvSubtitle.setText("Bước 1/3: Xác minh tài khoản để nhận mã OTP 6 chữ số");
            } else if (step == 2) {
                tvSubtitle.setText("Bước 2/3: Nhập mã OTP từ tin nhắn SMS cho \"" + verifiedPhoneHolder[0] + "\"");
            } else {
                tvSubtitle.setText("Bước 3/3: Nhập mật khẩu mới cho tài khoản \"" + verifiedPhoneHolder[0] + "\"");
            }
        };
        java.util.function.LongConsumer startForgotCountdown = durationMs -> {
            cancelForgotOtpTimer();
            forgotOtpTimer = startOtpCountdown(
                    durationMs,
                    tvForgotResendCountdown,
                    btnForgotResendOtp,
                    () -> isAdded() && dialog.isShowing());
        };

        if (btnForgotResendOtp != null) {
            btnForgotResendOtp.setOnClickListener(v -> {
                btnForgotResendOtp.setEnabled(false);
                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("phone", verifiedPhoneHolder[0]);

                apiService.requestForgotPasswordOtp(reqBody).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded() || !dialog.isShowing()) return;
                        btnForgotResendOtp.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                            verifiedOtpHolder[0] = "";
                            if (etForgotOtpCode != null) etForgotOtpCode.setText("");
                            Toast.makeText(requireContext(), "Đã gửi lại mã OTP mới qua SMS!", Toast.LENGTH_SHORT).show();
                            startForgotCountdown.accept(180000L);
                        } else {
                            String err = extractErrorMessage(response, "Gửi lại mã thất bại!");
                            Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded() || !dialog.isShowing()) return;
                        btnForgotResendOtp.setEnabled(true);
                        Toast.makeText(requireContext(), "Lỗi mạng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        // Tự động điền nếu người dùng đã gõ sẵn tài khoản ở màn hình đăng nhập
        String typedPhone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        if (!typedPhone.isEmpty()) {
            etForgotContact.setText(typedPhone);
        }


        dialog.setOnDismissListener(d -> {
            cancelForgotOtpTimer();
            if (forgotPasswordDialog == dialog) {
                forgotPasswordDialog = null;
            }
        });
        btnCancelStep1.setOnClickListener(v -> dialog.dismiss());

        // BƯỚC 1: Bấm "Gửi SMS OTP"
        btnRequestOtp.setOnClickListener(v -> {
            final String contactInput = normalizeLoginPhone(etForgotContact.getText() != null
                    ? etForgotContact.getText().toString() : "");
            if (contactInput.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập số điện thoại hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            btnRequestOtp.setEnabled(false);

            Map<String, Object> body = new HashMap<>();
            body.put("phone", contactInput);

            apiService.requestForgotPasswordOtp(body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    btnRequestOtp.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                        String resolvedUser = contactInput;
                        String maskedContact = response.body().get("maskedContact") != null
                                ? String.valueOf(response.body().get("maskedContact"))
                                : contactInput;

                        verifiedPhoneHolder[0] = resolvedUser;
                        verifiedOtpHolder[0] = "";
                        if (etForgotOtpCode != null) etForgotOtpCode.setText("");
                        if (etNewPassword != null) etNewPassword.setText("");
                        if (etConfirmPassword != null) etConfirmPassword.setText("");

                        // Chuyển sang giao diện Bước 2 (chỉ nhập mã OTP)
                        showStep.accept(2);
                        if (tvOtpDeliveryStatus != null) {
                            tvOtpDeliveryStatus.setText("Đã gửi tin nhắn SMS chứa mã OTP (6 chữ số) tới " + maskedContact + ".");
                        }
                        if (etForgotOtpCode != null) etForgotOtpCode.requestFocus();

                        startForgotCountdown.accept(180000L);
                        Toast.makeText(requireContext(), "Đã gửi tin nhắn SMS OTP tới " + maskedContact + "! Vui lòng kiểm tra tin nhắn trên điện thoại.", Toast.LENGTH_LONG).show();
                    } else if (response.code() == 429) {
                        // Mã OTP đã gửi trước đó vẫn còn hạn -> Cho phép vào Bước 2 với đúng thời gian chờ còn lại
                        String errMsg = extractErrorMessage(response, "Mã OTP đã gửi trước đó vẫn còn hiệu lực.");
                        verifiedPhoneHolder[0] = contactInput;
                        verifiedOtpHolder[0] = "";
                        if (etForgotOtpCode != null) etForgotOtpCode.setText("");
                        if (etNewPassword != null) etNewPassword.setText("");
                        if (etConfirmPassword != null) etConfirmPassword.setText("");

                        showStep.accept(2);
                        if (tvOtpDeliveryStatus != null) {
                            tvOtpDeliveryStatus.setText("Mã OTP đã được gửi tới " + contactInput + " và vẫn còn hiệu lực (3 phút).");
                        }
                        if (etForgotOtpCode != null) etForgotOtpCode.requestFocus();
                        startForgotCountdown.accept(extractOtpCooldownMillis(errMsg));
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_SHORT).show();
                    } else {
                        String errMsg = extractErrorMessage(response, "Thông tin xác minh không khớp với tài khoản!");
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    btnRequestOtp.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Nút "Quay lại" từ Bước 2 về Bước 1
        btnBackToStep1.setOnClickListener(v -> {
            cancelForgotOtpTimer();
            showStep.accept(1);
        });

        // BƯỚC 2: Xác thực OTP trước khi chuyển sang màn hình nhập mật khẩu mới (Bước 3)
        btnVerifyForgotOtp.setOnClickListener(v -> {
            String otpInput = etForgotOtpCode.getText() != null ? etForgotOtpCode.getText().toString().trim() : "";
            if (otpInput.length() != 6) {
                Toast.makeText(requireContext(), "Vui lòng nhập đủ 6 chữ số mã OTP!", Toast.LENGTH_SHORT).show();
                etForgotOtpCode.requestFocus();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            btnVerifyForgotOtp.setEnabled(false);

            Map<String, Object> body = new HashMap<>();
            body.put("phone", verifiedPhoneHolder[0]);
            body.put("otp", otpInput);

            apiService.verifyForgotPasswordOtp(body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    btnVerifyForgotOtp.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                        verifiedOtpHolder[0] = otpInput;
                        showStep.accept(3);
                        if (etNewPassword != null) etNewPassword.requestFocus();
                        Toast.makeText(requireContext(), "Xác thực mã OTP thành công! Vui lòng đặt mật khẩu mới.", Toast.LENGTH_SHORT).show();
                    } else {
                        String errMsg = extractErrorMessage(response, "Mã OTP không đúng hoặc đã hết hạn!");
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    btnVerifyForgotOtp.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Nút "Quay lại" từ Bước 3 về Bước 2
        btnBackToStep2.setOnClickListener(v -> showStep.accept(2));

        // BƯỚC 3: Nhập mật khẩu mới & xác nhận lại mật khẩu -> Gửi yêu cầu đổi mật khẩu
        btnSubmitForgotPass.setOnClickListener(v -> {
            String otpInput = !verifiedOtpHolder[0].isEmpty()
                    ? verifiedOtpHolder[0]
                    : (etForgotOtpCode.getText() != null ? etForgotOtpCode.getText().toString().trim() : "");
            String newPass = etNewPassword.getText() != null ? etNewPassword.getText().toString() : "";
            String confirmPass = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

            if (otpInput.length() != 6) {
                Toast.makeText(requireContext(), "Mã OTP chưa hợp lệ, vui lòng xác thực lại!", Toast.LENGTH_SHORT).show();
                showStep.accept(2);
                return;
            }

            if (newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ mật khẩu mới và xác nhận mật khẩu!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (containsWhitespace(newPass)) {
                Toast.makeText(requireContext(), "Mật khẩu không được chứa khoảng trắng!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPass.length() < 6) {
                Toast.makeText(requireContext(), "Mật khẩu mới phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirmPass)) {
                Toast.makeText(requireContext(), "Mật khẩu xác nhận không trùng khớp!", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            btnSubmitForgotPass.setEnabled(false);

            Map<String, Object> body = new HashMap<>();
            body.put("phone", verifiedPhoneHolder[0]);
            body.put("otp", otpInput);
            body.put("newPassword", newPass);

            apiService.forgotPassword(body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    btnSubmitForgotPass.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                        cancelForgotOtpTimer();
                        dialog.dismiss();

                        if (response.body().get("user") instanceof Map) {
                            if (!applyAuthSessionFromResponse(response.body(), false)) {
                                clearAuthFormInputs();
                                if (etPhone != null) etPhone.setText(verifiedPhoneHolder[0]);
                                Toast.makeText(requireContext(), "Phản hồi tài khoản không hợp lệ! Vui lòng đăng nhập lại.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            clearAuthFormInputs();
                            checkLoginState();
                            Toast.makeText(requireContext(), "Xác thực SMS OTP & đặt mật khẩu thành công! Đã tự động đăng nhập.", Toast.LENGTH_LONG).show();
                        } else {
                            clearAuthFormInputs();
                            if (etPhone != null) etPhone.setText(verifiedPhoneHolder[0]);
                            Toast.makeText(requireContext(), "Đặt lại mật khẩu thành công! Bạn có thể bấm Đăng nhập ngay.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        String errMsg = extractErrorMessage(response, "Không thể đặt lại mật khẩu. Vui lòng thử lại!");
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    btnSubmitForgotPass.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void uploadAvatarToCloudinary(Uri imageUri) {
        if (!isAdded() || getView() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        Long userId = prefManager.getUserId();
        android.content.Context appContext = requireContext().getApplicationContext();
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        // Nén/resize ảnh ở luồng nền rồi gửi qua Backend Spring Boot
        new Thread(() -> {
            okhttp3.MultipartBody.Part filePart = CloudinaryHelper.prepareFilePart(appContext, imageUri, "file");
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) return;
                if (filePart == null) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Tệp ảnh không hợp lệ hoặc không thể đọc ảnh!", Toast.LENGTH_SHORT).show();
                    return;
                }

                apiService.uploadAvatarFile(userId, filePart).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded() || getView() == null) return;
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                            String newAvatarUrl = (String) response.body().get("avatarUrl");
                            if (newAvatarUrl != null && !newAvatarUrl.trim().isEmpty()) {
                                prefManager.updateAvatarUrl(newAvatarUrl);
                                Glide.with(AuthFragment.this)
                                        .load(newAvatarUrl)
                                        .placeholder(R.drawable.ic_avatar_placeholder)
                                        .error(R.drawable.ic_avatar_placeholder)
                                        .circleCrop()
                                        .into(ivAvatar);
                            }
                            Toast.makeText(requireContext(), "Tải ảnh đại diện qua máy chủ Artifex thành công!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(),
                                    extractErrorMessage(response, "Không thể tải ảnh đại diện lên máy chủ!"),
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded() || getView() == null) return;
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }).start();
    }

    // ================= SỔ ĐỊA CHỈ (CHỌN MẶC ĐỊNH, THÊM, SỬA, XÁC NHẬN XÓA) =================
    private void showAddressBookBottomSheet() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_address_book, null);
        bottomSheet.setContentView(sheetView);

        RecyclerView rvAddresses = sheetView.findViewById(R.id.rvAddresses);
        Button btnAddNew = sheetView.findViewById(R.id.btnAddNewAddress);

        rvAddresses.setLayoutManager(new LinearLayoutManager(requireContext()));
        List<UserAddress> addressList = prefManager.getAddressList();

        final AddressAdapter[] adapterHolder = new AddressAdapter[1];
        adapterHolder[0] = new AddressAdapter(addressList, new AddressAdapter.OnAddressListener() {
            @Override
            public void onSelect(UserAddress address) {
                prefManager.setDefaultAddress(address.getId());
                bindProfileData();
                syncDefaultAddressToBackend(address.getAddressLine());
                bottomSheet.dismiss();
                Toast.makeText(requireContext(), "Đã chọn địa chỉ: " + address.getTag(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onEdit(UserAddress address) {
                showAddOrEditAddressDialog(bottomSheet, adapterHolder[0], addressList, address);
            }

            @Override
            public void onDelete(UserAddress address) {
                showConfirmDeleteAddressDialog(address, adapterHolder[0], addressList);
            }
        });

        rvAddresses.setAdapter(adapterHolder[0]);

        btnAddNew.setOnClickListener(v -> showAddOrEditAddressDialog(bottomSheet, adapterHolder[0], addressList, null));

        bottomSheet.show();
    }

    private void showConfirmDeleteAddressDialog(UserAddress address, AddressAdapter adapter, List<UserAddress> addressList) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận xóa địa chỉ")
                .setMessage("Bạn có chắc chắn muốn xóa địa chỉ nhận hàng của \"" + address.getReceiverName() + "\" không?")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Xóa", (dialog, which) -> {
                    prefManager.deleteAddress(address.getId());
                    if (addressList != null) {
                        addressList.clear();
                        addressList.addAll(prefManager.getAddressList());
                    }
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    bindProfileData();
                    syncDefaultAddressToBackend(prefManager.getAddress());
                    Toast.makeText(requireContext(), "Đã xóa địa chỉ!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void syncDefaultAddressToBackend(String addressLine) {
        Map<String, Object> body = new HashMap<>();
        body.put("address", addressLine != null ? addressLine : "");
        apiService.updateProfile(prefManager.getUserId(), body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
        });
    }

    private void showAddAddressDialog() {
        showAddOrEditAddressDialog(null, null, null, null);
    }

    private void showAddOrEditAddressDialog(
            BottomSheetDialog parentSheet,
            AddressAdapter adapter,
            List<UserAddress> addressList,
            @Nullable UserAddress editingAddress) {

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_address, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogAddressTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogAddressSubtitle);
        TextInputEditText etReceiverName = dialogView.findViewById(R.id.etNewReceiverName);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etNewPhone);
        TextInputEditText etAddressLine = dialogView.findViewById(R.id.etNewAddressLine);
        RadioButton rbHome = dialogView.findViewById(R.id.rbTagHome);
        RadioButton rbOffice = dialogView.findViewById(R.id.rbTagOffice);
        CheckBox cbSetDefault = dialogView.findViewById(R.id.cbSetDefault);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelAddAddress);
        Button btnSave = dialogView.findViewById(R.id.btnSaveNewAddress);

        final boolean isEditMode = (editingAddress != null);

        if (isEditMode) {
            if (tvTitle != null) tvTitle.setText("Chỉnh sửa địa chỉ");
            if (tvSubtitle != null) tvSubtitle.setText("Cập nhật thông tin người nhận và địa chỉ giao hàng");
            btnSave.setText("Cập nhật");

            etReceiverName.setText(editingAddress.getReceiverName());
            etPhone.setText(editingAddress.getPhone());
            etAddressLine.setText(editingAddress.getAddressLine());

            if (editingAddress.getTag() != null && editingAddress.getTag().contains("Văn phòng")) {
                if (rbOffice != null) rbOffice.setChecked(true);
            } else {
                if (rbHome != null) rbHome.setChecked(true);
            }
            if (cbSetDefault != null) {
                cbSetDefault.setChecked(editingAddress.isDefault());
            }
        } else {
            etReceiverName.setText(prefManager.getFullName());
            etPhone.setText(prefManager.getPhone());
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etReceiverName.getText() != null ? etReceiverName.getText().toString().trim() : "";
            String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
            String address = etAddressLine.getText() != null ? etAddressLine.getText().toString().trim() : "";
            String tag = (rbHome != null && rbHome.isChecked()) ? "🏠 Nhà riêng" : "🏢 Văn phòng";
            boolean isDefault = cbSetDefault != null && cbSetDefault.isChecked();

            if (name.isEmpty() || phone.isEmpty() || address.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isEditMode) {
                editingAddress.setReceiverName(name);
                editingAddress.setPhone(phone);
                editingAddress.setAddressLine(address);
                editingAddress.setTag(tag);
                editingAddress.setDefault(isDefault);
                prefManager.updateAddress(editingAddress);
            } else {
                UserAddress newAddr = new UserAddress(name, phone, address, tag, isDefault);
                prefManager.addAddress(newAddr);
            }

            if (addressList != null) {
                addressList.clear();
                addressList.addAll(prefManager.getAddressList());
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            refreshAddressList();

            // Đồng bộ địa chỉ mặc định mới nhất lên Backend
            syncDefaultAddressToBackend(prefManager.getAddress());

            dialog.dismiss();
            Toast.makeText(
                    requireContext(),
                    isEditMode ? "Cập nhật địa chỉ thành công!" : "Thêm địa chỉ thành công!",
                    Toast.LENGTH_SHORT
            ).show();
        });

        dialog.show();
    }

    private void showChangePasswordDialog() {
        if (prefManager.isGoogleAccount()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Tài khoản liên kết Google")
                    .setMessage("Tài khoản của bạn đang đăng nhập bằng Google nên không thể đổi mật khẩu trực tiếp trong ứng dụng Artifex.\n\nVui lòng tự thay đổi mật khẩu trực tiếp ở tài khoản Google bên ngoài!")
                    .setPositiveButton("Đã hiểu", (d, which) -> d.dismiss())
                    .show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextInputEditText etOldPassword = dialogView.findViewById(R.id.etOldPassword);
        TextInputEditText etNewPassword = dialogView.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirmNewPassword = dialogView.findViewById(R.id.etConfirmNewPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelChangePass);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmitChangePass);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String oldPass = etOldPassword.getText() != null ? etOldPassword.getText().toString() : "";
            String newPass = etNewPassword.getText() != null ? etNewPassword.getText().toString() : "";
            String confirmNewPass = etConfirmNewPassword.getText() != null ? etConfirmNewPassword.getText().toString() : "";

            if (oldPass.isEmpty() || newPass.isEmpty() || confirmNewPass.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (containsWhitespace(newPass)) {
                Toast.makeText(requireContext(), "Mật khẩu không được chứa khoảng trắng!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPass.length() < 6) {
                Toast.makeText(requireContext(), "Mật khẩu mới phải có ít nhất 6 ký tự!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirmNewPass)) {
                Toast.makeText(requireContext(), "Mật khẩu xác nhận không trùng khớp!", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(false);

            Map<String, Object> body = new HashMap<>();
            body.put("oldPassword", oldPass);
            body.put("newPassword", newPass);

            Long userId = prefManager.getUserId();
            apiService.changePassword(userId, body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);

                    if (response.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(requireContext(), "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                    } else if (response.code() == 400) {
                        String errMsg = extractErrorMessage(response, "Mật khẩu hiện tại không đúng hoặc tài khoản không hỗ trợ!");
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), "Đổi mật khẩu thất bại (Mã lỗi: " + response.code() + ")!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    if (!isAdded() || !dialog.isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private boolean applyAuthSessionFromResponse(Map<String, Object> body, boolean forceGoogleAccount) {
        if (body == null || !(body.get("user") instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> userMap = (Map<String, Object>) body.get("user");
        if (!saveUserDataToPref(userMap)) {
            return false;
        }
        String token = (String) body.get("token");
        if (token != null) {
            prefManager.saveAuthToken(token);
        }
        if (forceGoogleAccount) {
            prefManager.setGoogleAccount(true);
        }
        return true;
    }

    private boolean saveUserDataToPref(Map<String, Object> user) {
        if (user == null || !(user.get("id") instanceof Number)) {
            return false;
        }
        long idValue = ((Number) user.get("id")).longValue();
        if (idValue <= 0) {
            return false;
        }
        Long id = idValue;
        String firebaseUid = (String) user.get("firebaseUid");
        String username = (String) user.get("username");
        String email = (String) user.get("email");
        String fullName = (String) user.get("fullName");
        String phone = (String) user.get("phone");
        String address = (String) user.get("address");
        String avatarUrl = (String) user.get("avatarUrl");
        String dob = (String) user.get("dateOfBirth");
        String gender = (String) user.get("gender");

        boolean isGoogleAccount = (firebaseUid != null && !firebaseUid.trim().isEmpty())
                || (username != null && username.startsWith("fb_"));

        prefManager.saveUser(id, username, email, fullName, phone, address, avatarUrl);
        prefManager.setGoogleAccount(isGoogleAccount);
        prefManager.updatePersonalInfo(fullName, email, phone, dob, gender != null ? gender : "Nam");
        return true;
    }

    private void clearAuthFormInputs() {
        if (etFullName != null) etFullName.setText("");
        if (etEmail != null) etEmail.setText("");
        if (etPhone != null) etPhone.setText("");
        if (etPassword != null) etPassword.setText("");
        if (etConfirmPassword != null) etConfirmPassword.setText("");
        clearEmailError();
    }

    private void resetAvatarPlaceholder() {
        if (!isAdded() || ivAvatar == null) return;
        Glide.with(this).clear(ivAvatar);
        ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
    }

    private void signOutFirebaseClient(boolean clearCredentialState) {
        if (!isAdded()) return;
        mAuth = FirebaseAuthHelper.getFirebaseAuth(requireContext());
        if (mAuth != null) {
            mAuth.signOut();
        }
        if (!clearCredentialState) return;
        try {
            androidx.credentials.CredentialManager credentialManager =
                    androidx.credentials.CredentialManager.create(requireContext());
            androidx.credentials.ClearCredentialStateRequest clearRequest =
                    new androidx.credentials.ClearCredentialStateRequest();
            credentialManager.clearCredentialStateAsync(
                    clearRequest,
                    new android.os.CancellationSignal(),
                    ContextCompat.getMainExecutor(requireContext()),
                    new androidx.credentials.CredentialManagerCallback<Void, androidx.credentials.exceptions.ClearCredentialException>() {
                        @Override
                        public void onResult(Void result) {}

                        @Override
                        public void onError(@NonNull androidx.credentials.exceptions.ClearCredentialException e) {}
                    });
        } catch (Exception ignored) {}
    }

    private void cancelRegisterOtpTimer() {
        if (registerOtpTimer != null) {
            registerOtpTimer.cancel();
            registerOtpTimer = null;
        }
    }

    private void cancelForgotOtpTimer() {
        if (forgotOtpTimer != null) {
            forgotOtpTimer.cancel();
            forgotOtpTimer = null;
        }
    }

    private CountDownTimer startOtpCountdown(
            long durationMillis,
            TextView tvCountdown,
            View btnResend,
            java.util.function.BooleanSupplier isUiActive) {
        if (durationMillis <= 0) {
            if (tvCountdown != null) tvCountdown.setVisibility(View.GONE);
            if (btnResend != null) btnResend.setVisibility(View.VISIBLE);
            return null;
        }
        return new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!isUiActive.getAsBoolean()) {
                    cancel();
                    return;
                }
                if (tvCountdown != null) {
                    tvCountdown.setVisibility(View.VISIBLE);
                    long totalSec = millisUntilFinished / 1000;
                    long min = totalSec / 60;
                    long sec = totalSec % 60;
                    String timeStr = min > 0
                            ? String.format(Locale.getDefault(), "%d:%02d", min, sec)
                            : totalSec + "s";
                    tvCountdown.setText("Gửi lại mã sau " + timeStr);
                }
                if (btnResend != null) {
                    btnResend.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFinish() {
                if (!isUiActive.getAsBoolean()) return;
                if (tvCountdown != null) {
                    tvCountdown.setVisibility(View.GONE);
                }
                if (btnResend != null) {
                    btnResend.setVisibility(View.VISIBLE);
                }
            }
        }.start();
    }

    private long extractOtpCooldownMillis(String message) {
        if (message == null) return 0L;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)\\s*giây")
                .matcher(message);
        if (matcher.find()) {
            try {
                long seconds = Long.parseLong(matcher.group(1));
                if (seconds > 0) {
                    return Math.min(seconds, 180L) * 1000L;
                }
            } catch (NumberFormatException ignored) {}
        }
        return 0L;
    }

    // ==================== RECOMMENDATIONS (ĐỘC LẬP TRONG PACKAGE UI/AUTH) ====================

    private void loadRecommendationProducts() {
        if (!isAdded() || !prefManager.isLoggedIn()) return;

        if (progressRecommendations != null) {
            progressRecommendations.setVisibility(View.VISIBLE);
        }

        apiService.getAuthRecommendedProducts(20).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                if (progressRecommendations != null) progressRecommendations.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        Object dataObj = response.body().get("data");
                        if (dataObj instanceof Map) {
                            Object contentObj = ((Map<?, ?>) dataObj).get("content");
                            if (contentObj instanceof List) {
                                List<?> rawList = (List<?>) contentObj;
                                List<RecommendedProduct> parsedList = parseRecommendedProductsFromBackend(rawList);
                                if (!parsedList.isEmpty() && recommendedProductAdapter != null) {
                                    recommendedProductAdapter.updateData(parsedList);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                if (progressRecommendations != null) progressRecommendations.setVisibility(View.GONE);
            }
        });
    }

    private List<RecommendedProduct> parseRecommendedProductsFromBackend(List<?> rawList) {
        List<RecommendedProduct> result = new ArrayList<>();

        for (int i = 0; i < rawList.size(); i++) {
            Object item = rawList.get(i);
            if (item instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) item;
                long id = map.get("id") instanceof Number ? ((Number) map.get("id")).longValue() : (i + 1L);
                String categoryName = map.get("categoryName") != null ? map.get("categoryName").toString() : "";
                String name = map.get("name") != null ? map.get("name").toString() : "";
                String description = map.get("description") != null ? map.get("description").toString() : "";
                long price = map.get("price") instanceof Number ? ((Number) map.get("price")).longValue() : 0L;
                int stock = map.get("stock") instanceof Number ? ((Number) map.get("stock")).intValue() : 0;
                double ratingAvg = map.get("ratingAvg") instanceof Number ? ((Number) map.get("ratingAvg")).doubleValue() : 5.0;
                int reviewCount = map.get("reviewCount") instanceof Number ? ((Number) map.get("reviewCount")).intValue() : 0;
                String imageUrl = map.get("imageUrl") != null ? map.get("imageUrl").toString() : "";

                result.add(new RecommendedProduct(
                        id,
                        categoryName,
                        name,
                        description,
                        price,
                        stock,
                        ratingAvg,
                        reviewCount,
                        imageUrl
                ));
            }
        }
        return result;
    }

    private void showProductDetailBottomSheet(RecommendedProduct product) {
        if (!isAdded() || product == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_product_preview, null);
        dialog.setContentView(sheetView);

        ImageView ivPreview = sheetView.findViewById(R.id.ivPreviewImage);
        TextView tvCategory = sheetView.findViewById(R.id.tvPreviewCategory);
        TextView tvStock = sheetView.findViewById(R.id.tvPreviewStock);
        TextView tvName = sheetView.findViewById(R.id.tvPreviewName);
        TextView tvPrice = sheetView.findViewById(R.id.tvPreviewPrice);
        TextView tvRatingScore = sheetView.findViewById(R.id.tvPreviewRatingScore);
        TextView tvReviewCount = sheetView.findViewById(R.id.tvPreviewReviewCount);
        TextView tvDesc = sheetView.findViewById(R.id.tvPreviewDescription);
        View btnClose = sheetView.findViewById(R.id.btnClosePreview);
        View btnViewDetail = sheetView.findViewById(R.id.btnViewDetailPreview);
        View btnExploreStore = sheetView.findViewById(R.id.btnExploreStorePreview);

        tvName.setText(product.getName());

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,### đ", symbols);
        tvPrice.setText(formatter.format(product.getPrice()));

        tvRatingScore.setText(String.format(Locale.US, "%.1f / 5.0", product.getRatingAvg()));
        tvReviewCount.setText("(" + product.getReviewCount() + " đánh giá)");
        tvDesc.setText(!product.getDescription().isEmpty()
                ? product.getDescription()
                : "Sản phẩm chính hãng phân phối bởi Artifex PC Gear Store.");

        if (!product.getCategoryName().isEmpty()) {
            tvCategory.setText(product.getCategoryName().toUpperCase(Locale.ROOT));
            tvCategory.setVisibility(View.VISIBLE);
        } else {
            tvCategory.setVisibility(View.GONE);
        }

        if (product.getStock() > 0) {
            tvStock.setText("✓ Còn hàng: " + product.getStock());
            tvStock.setTextColor(ContextCompat.getColor(requireContext(), R.color.artifex_success));
        } else {
            tvStock.setText("✕ Hết hàng");
            tvStock.setTextColor(ContextCompat.getColor(requireContext(), R.color.artifex_error));
        }

        Glide.with(this)
                .load(product.getImageUrl())
                .placeholder(R.color.artifex_border)
                .error(R.color.artifex_border)
                .centerCrop()
                .into(ivPreview);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnViewDetail != null) {
            btnViewDetail.setOnClickListener(v -> {
                dialog.dismiss();
                Bundle resultBundle = new Bundle();
                resultBundle.putLong("productId", product.getId());
                getParentFragmentManager().setFragmentResult("selected_product", resultBundle);
                if (getActivity() instanceof MainActivity) {
                    BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
                    if (nav != null) {
                        nav.setSelectedItemId(R.id.nav_detail);
                    }
                }
            });
        }

        if (btnExploreStore != null) {
            btnExploreStore.setOnClickListener(v -> {
                dialog.dismiss();
                if (getActivity() instanceof MainActivity) {
                    BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
                    if (nav != null) {
                        nav.setSelectedItemId(R.id.nav_catalog);
                    }
                }
            });
        }

        dialog.show();
    }
}