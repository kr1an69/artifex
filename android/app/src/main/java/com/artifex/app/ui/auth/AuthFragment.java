package com.artifex.app.ui.auth;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
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
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private TextInputLayout tilFullName, tilUsername, tilEmail, tilPhone, tilPassword, tilConfirmPassword;
    private TextInputEditText etFullName, etUsername, etEmail, etPhone, etPassword, etConfirmPassword;
    private Button btnSubmitAuth, btnFirebaseAuth;
    private View btnLogout, btnChangePassword, cardShippingInfo;
    private View btnViewAllOrders, btnOrderPending, btnOrderProcessing, btnOrderShipping, btnOrderCompleted;
    private TextView tvAuthTitle, tvToggleMode, tvForgotPassword;
    private ProgressBar progressBar;

    private ImageView ivAvatar;
    private ImageButton btnChangeAvatar;
    private ImageView btnEditPersonalInfo;
    private TextView tvProfileFullName, tvProfileEmail;

    // Ô duy nhất hiển thị địa chỉ giao hàng được chọn
    private View cardSelectedAddress, layoutEmptyAddress, btnEmptyAddAddress;
    private View btnAddNewAddressMain;
    private TextView tvSelectedReceiverName, tvSelectedTag, tvSelectedDefaultBadge, tvSelectedPhone, tvSelectedAddressLine;

    private boolean isRegisterMode = false;
    private ApiService apiService;
    private SharedPrefManager prefManager;
    private FirebaseAuth mAuth;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        uploadAvatarToCloudinary(selectedImageUri);
                    }
                }
            }
    );

    // Launcher mở màn hình Chọn tài khoản / Đăng nhập Google thật của hệ điều hành Android (Google Play Services)
    private final ActivityResultLauncher<Intent> googleAccountPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String googleEmail = result.getData().getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (googleEmail != null && !googleEmail.trim().isEmpty()) {
                        String cleanEmail = googleEmail.trim().toLowerCase();
                        String defaultName = cleanEmail.contains("@") ? cleanEmail.split("@")[0] : cleanEmail;
                        String generatedUid = "google_uid_" + Math.abs(cleanEmail.hashCode());

                        // Đồng bộ ngầm lên Firebase Auth (nếu đã cấu hình Firebase) rồi lưu vào MySQL
                        FirebaseAuthHelper.syncUserToFirebaseSilently(requireContext(), cleanEmail, "GoogleAuth@123", defaultName);
                        syncFirebaseUserToBackend(generatedUid, cleanEmail, defaultName, "", true);
                    }
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
        tilUsername = v.findViewById(R.id.tilUsername);
        tilEmail = v.findViewById(R.id.tilEmail);
        tilPhone = v.findViewById(R.id.tilPhone);
        tilPassword = v.findViewById(R.id.tilPassword);
        tilConfirmPassword = v.findViewById(R.id.tilConfirmPassword);
        etFullName = v.findViewById(R.id.etFullName);
        etUsername = v.findViewById(R.id.etUsername);
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
    }

    private void setupEvents() {
        tvToggleMode.setOnClickListener(v -> toggleAuthMode());

        if (etEmail != null) {
            etEmail.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (!isRegisterMode || tilEmail == null) return;
                    String input = s != null ? s.toString().trim().toLowerCase() : "";
                    if (input.isEmpty()) {
                        tilEmail.setError(null);
                        tilEmail.setErrorEnabled(false);
                    } else if (!isValidGmail(input)) {
                        tilEmail.setErrorEnabled(true);
                        tilEmail.setError("Email phải có định dạng @gmail.com");
                    } else {
                        tilEmail.setError(null);
                        tilEmail.setErrorEnabled(false);
                    }
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {}
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
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

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

        View.OnClickListener orderDevClickListener = v ->
                Toast.makeText(requireContext(), "Tính năng Đơn mua đang được phát triển!", Toast.LENGTH_SHORT).show();

        if (btnViewAllOrders != null) btnViewAllOrders.setOnClickListener(orderDevClickListener);
        if (btnOrderPending != null) btnOrderPending.setOnClickListener(orderDevClickListener);
        if (btnOrderProcessing != null) btnOrderProcessing.setOnClickListener(orderDevClickListener);
        if (btnOrderShipping != null) btnOrderShipping.setOnClickListener(orderDevClickListener);
        if (btnOrderCompleted != null) btnOrderCompleted.setOnClickListener(orderDevClickListener);

        btnLogout.setOnClickListener(v -> showConfirmLogoutDialog());
    }

    private void showConfirmLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi tài khoản hiện tại không?")
                .setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    prefManager.clear();
                    mAuth = FirebaseAuthHelper.getFirebaseAuth(requireContext());
                    if (mAuth != null) {
                        mAuth.signOut();
                    }
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
                tilEmail.setError(null);
                tilEmail.setErrorEnabled(false);
            }
            if (tilUsername != null) {
                tilUsername.setHint("Tên đăng nhập");
            }
            if (etUsername != null) {
                etUsername.setHint(null);
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
                tilEmail.setError(null);
                tilEmail.setErrorEnabled(false);
            }
            if (tilUsername != null) {
                tilUsername.setHint("Tên đăng nhập hoặc Số điện thoại");
            }
            if (etUsername != null) {
                etUsername.setHint(null);
            }
            if (tilPhone != null) {
                tilPhone.setVisibility(View.GONE);
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
                if (response.isSuccessful() && response.body() != null && response.body().get("user") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> userMap = (Map<String, Object>) response.body().get("user");
                    saveUserDataToPref(userMap);
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

        // Cập nhật danh sách địa chỉ giao hàng hiển thị trực tiếp
        refreshAddressList();

        String avatarUrl = prefManager.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .into(ivAvatar);
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

            if (newEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                Toast.makeText(requireContext(), "Địa chỉ email không hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            btnSave.setEnabled(false);

            // Đồng bộ lên MySQL backend
            Map<String, Object> body = new HashMap<>();
            body.put("fullName", newFullName);
            body.put("email", newEmail);
            body.put("phone", newPhone);
            body.put("dateOfBirth", newDob);
            body.put("gender", selectedGender);

            final String finalGender = selectedGender;
            apiService.updateProfile(prefManager.getUserId(), body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);

                    if (response.isSuccessful()) {
                        prefManager.updatePersonalInfo(newFullName, newEmail, newPhone, newDob, finalGender);
                        bindProfileData();
                        dialog.dismiss();
                        Toast.makeText(requireContext(), "Cập nhật thông tin cá nhân thành công!", Toast.LENGTH_SHORT).show();
                    } else {
                        String errorMsg = "Cập nhật thất bại!";
                        if (response.code() == 400) {
                            errorMsg = "Email đã được sử dụng bởi tài khoản khác!";
                        }
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    btnSave.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void handleLogin() {
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập tài khoản và mật khẩu", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> body = new HashMap<>();
        body.put("usernameOrEmail", username);
        body.put("password", password);

        apiService.login(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> userMap = (Map<String, Object>) response.body().get("user");
                    saveUserDataToPref(userMap);

                    // Đồng bộ ngầm tài khoản lên Firebase Authentication để luôn có session Firebase thật
                    String resolvedEmail = (String) userMap.get("email");
                    String resolvedName = (String) userMap.get("fullName");
                    FirebaseAuthHelper.syncUserToFirebaseSilently(requireContext(), resolvedEmail, password, resolvedName);

                    checkLoginState();
                    Toast.makeText(requireContext(), "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Sai thông tin đăng nhập!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isValidGmail(String email) {
        if (email == null) return false;
        String trimmed = email.trim().toLowerCase();
        return trimmed.matches("^[a-zA-Z0-9._%+-]+@gmail\\.com$");
    }

    private void handleRegister() {
        String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim().toLowerCase() : "";
        String phone = (etPhone != null && etPhone.getText() != null) ? etPhone.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = (etConfirmPassword != null && etConfirmPassword.getText() != null)
                ? etConfirmPassword.getText().toString().trim() : "";

        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ tất cả thông tin!", Toast.LENGTH_SHORT).show();
            if (email.isEmpty() && tilEmail != null) {
                tilEmail.setError("Vui lòng nhập địa chỉ Gmail!");
            }
            return;
        }

        if (!isValidGmail(email)) {
            if (tilEmail != null) {
                tilEmail.setError("Email phải có định dạng @gmail.com (ví dụ: name@gmail.com)");
            }
            Toast.makeText(requireContext(), "Email phải có định dạng @gmail.com!", Toast.LENGTH_SHORT).show();
            etEmail.requestFocus();
            return;
        } else {
            if (tilEmail != null) {
                tilEmail.setError(null);
            }
        }

        String cleanPhone = phone.replaceAll("[^0-9]", "");
        if (cleanPhone.length() != 10 || !cleanPhone.startsWith("0")) {
            Toast.makeText(requireContext(), "Số điện thoại không hợp lệ! Vui lòng nhập đúng 10 số (ví dụ: 09xxxxxxxx) để nhận SMS OTP.", Toast.LENGTH_SHORT).show();
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
        reqBody.put("username", username);
        reqBody.put("phone", cleanPhone);
        reqBody.put("email", email);

        apiService.sendRegisterOtp(reqBody).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                progressBar.setVisibility(View.GONE);
                btnSubmitAuth.setEnabled(true);

                if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                    String msg = (String) response.body().get("message");
                    Toast.makeText(requireContext(), msg != null ? msg : "Đã gửi mã xác thực OTP qua tin nhắn SMS!", Toast.LENGTH_LONG).show();
                    showRegisterOtpDialog(fullName, username, email, cleanPhone, password);
                } else {
                    String errorMsg = extractErrorMessage(response, "Gửi mã OTP thất bại!");
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                btnSubmitAuth.setEnabled(true);
                Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRegisterOtpDialog(
            String fullName,
            String username,
            String email,
            String phone,
            String password) {

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_register_otp, null);
        builder.setView(dialogView);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

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

        CountDownTimer timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (tvResendCountdown != null) {
                    tvResendCountdown.setVisibility(View.VISIBLE);
                    tvResendCountdown.setText("Gửi lại mã sau " + (millisUntilFinished / 1000) + "s");
                }
                if (btnResendOtp != null) {
                    btnResendOtp.setVisibility(View.GONE);
                }
            }

            @Override
            public void onFinish() {
                if (tvResendCountdown != null) {
                    tvResendCountdown.setVisibility(View.GONE);
                }
                if (btnResendOtp != null) {
                    btnResendOtp.setVisibility(View.VISIBLE);
                }
            }
        }.start();

        if (btnResendOtp != null) {
            btnResendOtp.setOnClickListener(v -> {
                btnResendOtp.setEnabled(false);
                Map<String, Object> reqBody = new HashMap<>();
                reqBody.put("username", username);
                reqBody.put("phone", phone);
                reqBody.put("email", email);

                apiService.sendRegisterOtp(reqBody).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        btnResendOtp.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                            Toast.makeText(requireContext(), "Đã gửi lại mã OTP mới qua SMS!", Toast.LENGTH_SHORT).show();
                            timer.start();
                        } else {
                            String err = extractErrorMessage(response, "Gửi lại mã thất bại!");
                            Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
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
            regBody.put("username", username);
            regBody.put("email", email);
            regBody.put("phone", phone);
            regBody.put("password", password);
            regBody.put("otp", otpCode);

            apiService.register(regBody).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Xác nhận & Tạo tài khoản");

                    if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                        timer.cancel();
                        dialog.dismiss();

                        @SuppressWarnings("unchecked")
                        Map<String, Object> userMap = (Map<String, Object>) response.body().get("user");
                        saveUserDataToPref(userMap);

                        // Đồng bộ với Firebase Auth
                        FirebaseAuthHelper.syncUserToFirebaseSilently(requireContext(), email, password, fullName);

                        checkLoginState();
                        Toast.makeText(requireContext(), "Đăng ký tài khoản và xác thực số điện thoại thành công!", Toast.LENGTH_LONG).show();
                    } else {
                        String errMsg = extractErrorMessage(response, "Xác thực OTP thất bại!");
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setText("Xác nhận & Tạo tài khoản");
                    Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnCancel.setOnClickListener(v -> {
            timer.cancel();
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

                private void handleFirebaseLogin() {
        // Trường hợp 1: Nếu đã điền khóa Firebase OAuth, mở luồng Firebase OAuthProvider("google.com")
        if (FirebaseAuthHelper.isFirebaseConfigured(requireContext())) {
            progressBar.setVisibility(View.VISIBLE);
            FirebaseAuthHelper.signInWithRealGooglePopup(requireActivity(), new FirebaseAuthHelper.FirebaseAuthCallback() {
                @Override
                public void onSuccess(String firebaseUid, String email, String fullName, String avatarUrl, boolean isRealFirebaseSession) {
                    syncFirebaseUserToBackend(firebaseUid, email, fullName, avatarUrl, isRealFirebaseSession);
                }

                @Override
                public void onError(String errorMessage) {
                    progressBar.setVisibility(View.GONE);
                    launchSystemGoogleAccountPicker();
                }
            });
            return;
        }

        // Trường hợp 2 (Mặc định chạy ngay không cần tạo key):
        // Gọi trực tiếp màn hình Chọn tài khoản / Đăng nhập Google thật của Google Play Services trên máy!
        launchSystemGoogleAccountPicker();
    }

    private void launchSystemGoogleAccountPicker() {
        try {
            Intent googlePickerIntent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    new String[]{"com.google"},
                    null,
                    null,
                    null,
                    null
            );
            googleAccountPickerLauncher.launch(googlePickerIntent);
        } catch (ActivityNotFoundException e) {
            // Chỉ khi máy ảo không cài Google Play Services mới mở Dialog dự phòng
            showFirebaseAuthDialog();
        }
    }

    private void showFirebaseAuthDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_firebase_auth, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextInputEditText etFbEmail = dialogView.findViewById(R.id.etFirebaseEmail);
        TextInputEditText etFbPassword = dialogView.findViewById(R.id.etFirebasePassword);
        TextInputEditText etFbFullName = dialogView.findViewById(R.id.etFirebaseFullName);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelFirebaseDialog);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmitFirebaseDialog);

        String typedUser = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String typedEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String typedPass = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (!typedEmail.isEmpty()) {
            etFbEmail.setText(typedEmail);
        } else if (typedUser.contains("@")) {
            etFbEmail.setText(typedUser);
        }
        if (!typedPass.isEmpty()) {
            etFbPassword.setText(typedPass);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String email = etFbEmail.getText() != null ? etFbEmail.getText().toString().trim() : "";
            String password = etFbPassword.getText() != null ? etFbPassword.getText().toString().trim() : "";
            String fullName = etFbFullName.getText() != null ? etFbFullName.getText().toString().trim() : "";

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Vui lòng nhập địa chỉ Email hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(requireContext(), "Mật khẩu xác thực Firebase phải từ 6 ký tự trở lên!", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);
            btnSubmit.setEnabled(false);

            FirebaseAuthHelper.authenticateOrRegisterWithFirebase(
                    requireContext(),
                    email,
                    password,
                    fullName,
                    new FirebaseAuthHelper.FirebaseAuthCallback() {
                        @Override
                        public void onSuccess(String firebaseUid, String resolvedEmail, String resolvedName, String avatarUrl, boolean isRealFirebaseSession) {
                            btnSubmit.setEnabled(true);
                            dialog.dismiss();
                            syncFirebaseUserToBackend(firebaseUid, resolvedEmail, resolvedName, avatarUrl, isRealFirebaseSession);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            btnSubmit.setEnabled(true);
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });

        dialog.show();
    }

    private void syncFirebaseUserToBackend(String firebaseUid, String email, String fullName, String avatarUrl, boolean isRealSession) {
        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("fullName", fullName);
        body.put("avatarUrl", avatarUrl != null ? avatarUrl : "");
        body.put("firebaseUid", firebaseUid);

        apiService.firebaseLogin(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().get("user") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> userMap = (Map<String, Object>) response.body().get("user");
                    saveUserDataToPref(userMap);
                    checkLoginState();
                    Toast.makeText(requireContext(), "Đăng nhập Google thành công: " + email, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Không thể đồng bộ tài khoản Google với máy chủ!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Lỗi đồng bộ máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================= QUÊN MẬT KHẨU TÀI KHOẢN THƯỜNG (GỬI TIN NHẮN SMS OTP 6 SỐ QUA TEXTBEE) =================
    private void showForgotPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_forgot_password, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvSubtitle = dialogView.findViewById(R.id.tvForgotSubtitle);
        LinearLayout layoutStep1 = dialogView.findViewById(R.id.layoutForgotStep1);
        LinearLayout layoutStep2 = dialogView.findViewById(R.id.layoutForgotStep2);

        // Các View Bước 1
        TextInputEditText etForgotUsername = dialogView.findViewById(R.id.etForgotUsername);
        TextInputEditText etForgotContact = dialogView.findViewById(R.id.etForgotContact);
        Button btnCancelStep1 = dialogView.findViewById(R.id.btnCancelForgotStep1);
        Button btnRequestOtp = dialogView.findViewById(R.id.btnRequestOtp);

        // Các View Bước 2 (KHÔNG hiển thị lộ mã OTP trong form)
        TextView tvOtpDeliveryStatus = dialogView.findViewById(R.id.tvOtpDeliveryStatus);
        TextInputEditText etForgotOtpCode = dialogView.findViewById(R.id.etForgotOtpCode);
        TextInputEditText etNewPassword = dialogView.findViewById(R.id.etForgotNewPassword);
        TextInputEditText etConfirmPassword = dialogView.findViewById(R.id.etForgotConfirmPassword);
        Button btnBackToStep1 = dialogView.findViewById(R.id.btnBackToStep1);
        Button btnSubmitForgotPass = dialogView.findViewById(R.id.btnSubmitForgotPass);

        // Tự động điền nếu người dùng đã gõ sẵn tài khoản ở màn hình đăng nhập
        String typedUsername = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        if (!typedUsername.isEmpty()) {
            etForgotUsername.setText(typedUsername);
        }

        final String[] verifiedUsernameHolder = new String[]{""};

        btnCancelStep1.setOnClickListener(v -> dialog.dismiss());

        // BƯỚC 1: Bấm "Gửi SMS OTP"
        btnRequestOtp.setOnClickListener(v -> {
            final String usernameInput = etForgotUsername.getText() != null ? etForgotUsername.getText().toString().trim() : "";
            final String rawContactInput = etForgotContact.getText() != null ? etForgotContact.getText().toString().trim() : "";

            if (rawContactInput.isEmpty() && usernameInput.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập Số điện thoại để nhận tin nhắn SMS OTP!", Toast.LENGTH_SHORT).show();
                return;
            }
            final String contactInput = rawContactInput.isEmpty() ? usernameInput : rawContactInput;

            progressBar.setVisibility(View.VISIBLE);
            btnRequestOtp.setEnabled(false);

            Map<String, Object> body = new HashMap<>();
            body.put("username", usernameInput);
            body.put("emailOrPhone", contactInput);

            apiService.requestForgotPasswordOtp(body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    progressBar.setVisibility(View.GONE);
                    btnRequestOtp.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                        String resolvedUser = response.body().get("username") != null
                                ? String.valueOf(response.body().get("username"))
                                : usernameInput;
                        String maskedContact = response.body().get("maskedContact") != null
                                ? String.valueOf(response.body().get("maskedContact"))
                                : contactInput;

                        verifiedUsernameHolder[0] = resolvedUser;

                        // Chuyển sang giao diện Bước 2 (Chỉ gửi tin nhắn SMS về điện thoại thật, KHÔNG hiển thị trên máy ảo)
                        layoutStep1.setVisibility(View.GONE);
                        layoutStep2.setVisibility(View.VISIBLE);
                        if (tvSubtitle != null) {
                            tvSubtitle.setText("Bước 2/2: Nhập mã OTP từ tin nhắn SMS và đặt mật khẩu mới cho \"" + resolvedUser + "\"");
                        }
                        if (tvOtpDeliveryStatus != null) {
                            tvOtpDeliveryStatus.setText("Đã gửi tin nhắn SMS chứa mã OTP (6 chữ số) tới " + maskedContact + ".");
                        }

                        Toast.makeText(requireContext(), "Đã gửi tin nhắn SMS OTP tới " + maskedContact + "! Vui lòng kiểm tra tin nhắn trên điện thoại.", Toast.LENGTH_LONG).show();
                    } else {
                        String errMsg = "Thông tin xác minh không khớp với tài khoản!";
                        if (response.body() != null && response.body().get("message") != null) {
                            errMsg = String.valueOf(response.body().get("message"));
                        }
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    btnRequestOtp.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Nút "Quay lại" từ Bước 2 về Bước 1
        btnBackToStep1.setOnClickListener(v -> {
            layoutStep2.setVisibility(View.GONE);
            layoutStep1.setVisibility(View.VISIBLE);
            if (tvSubtitle != null) {
                tvSubtitle.setText("Bước 1/2: Xác minh tài khoản để nhận mã OTP 6 chữ số");
            }
        });

        // BƯỚC 2: Bấm "Đổi mật khẩu"
        btnSubmitForgotPass.setOnClickListener(v -> {
            String otpInput = etForgotOtpCode.getText() != null ? etForgotOtpCode.getText().toString().trim() : "";
            String newPass = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";
            String confirmPass = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

            if (otpInput.length() != 6) {
                Toast.makeText(requireContext(), "Vui lòng nhập đủ 6 chữ số mã OTP!", Toast.LENGTH_SHORT).show();
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
            body.put("username", verifiedUsernameHolder[0]);
            body.put("otp", otpInput);
            body.put("newPassword", newPass);

            apiService.forgotPassword(body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    progressBar.setVisibility(View.GONE);
                    btnSubmitForgotPass.setEnabled(true);

                    if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                        dialog.dismiss();
                        etUsername.setText(verifiedUsernameHolder[0]);
                        etPassword.setText(newPass);

                        if (response.body().get("user") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> userMap = (Map<String, Object>) response.body().get("user");
                            saveUserDataToPref(userMap);
                            checkLoginState();
                            Toast.makeText(requireContext(), "Xác thực SMS OTP & đặt mật khẩu thành công! Đã tự động đăng nhập.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(requireContext(), "Đặt lại mật khẩu thành công! Bạn có thể bấm Đăng nhập ngay.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        String errMsg = "Mã OTP không đúng hoặc đã hết hạn!";
                        if (response.body() != null && response.body().get("message") != null) {
                            errMsg = String.valueOf(response.body().get("message"));
                        }
                        Toast.makeText(requireContext(), errMsg, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    btnSubmitForgotPass.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void uploadAvatarToCloudinary(Uri imageUri) {
        progressBar.setVisibility(View.VISIBLE);
        CloudinaryHelper.uploadImage(requireContext(), imageUri, new CloudinaryHelper.UploadCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                Long userId = prefManager.getUserId();
                Map<String, Object> body = new HashMap<>();
                body.put("avatarUrl", imageUrl);

                apiService.updateAvatar(userId, body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful()) {
                            prefManager.updateAvatarUrl(imageUrl);
                            Glide.with(AuthFragment.this).load(imageUrl).circleCrop().into(ivAvatar);
                            Toast.makeText(requireContext(), "Đổi ảnh đại diện thành công!", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Lỗi lưu avatar: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
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
            String oldPass = etOldPassword.getText() != null ? etOldPassword.getText().toString().trim() : "";
            String newPass = etNewPassword.getText() != null ? etNewPassword.getText().toString().trim() : "";
            String confirmNewPass = etConfirmNewPassword.getText() != null ? etConfirmNewPassword.getText().toString().trim() : "";

            if (oldPass.isEmpty() || newPass.isEmpty() || confirmNewPass.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ thông tin!", Toast.LENGTH_SHORT).show();
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
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);

                    if (response.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(requireContext(), "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                    } else if (response.code() == 400) {
                        Toast.makeText(requireContext(), "Mật khẩu hiện tại không đúng hoặc tài khoản không hỗ trợ!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Đổi mật khẩu thất bại (Mã lỗi: " + response.code() + ")!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    Toast.makeText(requireContext(), "Lỗi kết nối máy chủ: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void saveUserDataToPref(Map<String, Object> user) {
        Long id = user.get("id") != null ? ((Number) user.get("id")).longValue() : 1L;
        String username = (String) user.get("username");
        String email = (String) user.get("email");
        String fullName = (String) user.get("fullName");
        String phone = (String) user.get("phone");
        String address = (String) user.get("address");
        String avatarUrl = (String) user.get("avatarUrl");
        String dob = (String) user.get("dateOfBirth");
        String gender = (String) user.get("gender");

        prefManager.saveUser(id, username, email, fullName, phone, address, avatarUrl);
        prefManager.updatePersonalInfo(fullName, email, phone, dob, gender != null ? gender : "Nam");
    }
}
