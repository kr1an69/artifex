package com.artifex.app.ui.auth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthFragment extends Fragment {

    private LinearLayout layoutAuthForm, layoutProfile;
    private TextInputLayout tilFullName, tilEmail, tilConfirmPassword;
    private TextInputEditText etFullName, etUsername, etEmail, etPassword, etConfirmPassword;
    private Button btnSubmitAuth, btnFirebaseAuth, btnEditProfile;
    private View btnLogout, btnChangePassword, cardShippingInfo;
    private TextView tvAuthTitle, tvToggleMode;
    private ProgressBar progressBar;

    private ImageView ivAvatar;
    private ImageButton btnChangeAvatar;
    private TextView tvProfileFullName, tvProfileEmail, tvProfilePhone, tvProfileAddress;

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
        try {
            mAuth = FirebaseAuth.getInstance();
        } catch (Exception e) {
            mAuth = null;
        }

        layoutAuthForm = v.findViewById(R.id.layoutAuthForm);
        layoutProfile = v.findViewById(R.id.layoutProfile);
        tilFullName = v.findViewById(R.id.tilFullName);
        tilEmail = v.findViewById(R.id.tilEmail);
        tilConfirmPassword = v.findViewById(R.id.tilConfirmPassword);
        etFullName = v.findViewById(R.id.etFullName);
        etUsername = v.findViewById(R.id.etUsername);
        etEmail = v.findViewById(R.id.etEmail);
        etPassword = v.findViewById(R.id.etPassword);
        etConfirmPassword = v.findViewById(R.id.etConfirmPassword);

        btnSubmitAuth = v.findViewById(R.id.btnSubmitAuth);
        btnFirebaseAuth = v.findViewById(R.id.btnFirebaseAuth);
        btnEditProfile = v.findViewById(R.id.btnEditProfile);
        btnChangePassword = v.findViewById(R.id.btnChangePassword);
        btnLogout = v.findViewById(R.id.btnLogout);
        cardShippingInfo = v.findViewById(R.id.cardShippingInfo);

        tvAuthTitle = v.findViewById(R.id.tvAuthTitle);
        tvToggleMode = v.findViewById(R.id.tvToggleMode);
        progressBar = v.findViewById(R.id.progressBar);

        ivAvatar = v.findViewById(R.id.ivAvatar);
        btnChangeAvatar = v.findViewById(R.id.btnChangeAvatar);
        tvProfileFullName = v.findViewById(R.id.tvProfileFullName);
        tvProfileEmail = v.findViewById(R.id.tvProfileEmail);
        tvProfilePhone = v.findViewById(R.id.tvProfilePhone);
        tvProfileAddress = v.findViewById(R.id.tvProfileAddress);
    }

    private void setupEvents() {
        tvToggleMode.setOnClickListener(v -> toggleAuthMode());

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

        // Bấm nút "Sổ địa chỉ" hoặc chạm vào thẻ Giao hàng đều mở Sổ địa chỉ
        btnEditProfile.setOnClickListener(v -> showAddressBookBottomSheet());
        if (cardShippingInfo != null) {
            cardShippingInfo.setOnClickListener(v -> showAddressBookBottomSheet());
        }

        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        btnLogout.setOnClickListener(v -> {
            prefManager.clear();
            if (mAuth != null) {
                mAuth.signOut();
            }
            checkLoginState();
            Toast.makeText(requireContext(), "Đã đăng xuất!", Toast.LENGTH_SHORT).show();
        });
    }

    private void toggleAuthMode() {
        isRegisterMode = !isRegisterMode;
        if (isRegisterMode) {
            tilFullName.setVisibility(View.VISIBLE);
            tilEmail.setVisibility(View.VISIBLE);
            if (tilConfirmPassword != null) {
                tilConfirmPassword.setVisibility(View.VISIBLE);
            }
            btnSubmitAuth.setText("Tạo Tài Khoản Mới");
            tvAuthTitle.setText("Đăng ký tài khoản Artifex");
            tvToggleMode.setText("Đã có tài khoản? Đăng nhập ngay");
        } else {
            tilFullName.setVisibility(View.GONE);
            tilEmail.setVisibility(View.GONE);
            if (tilConfirmPassword != null) {
                tilConfirmPassword.setVisibility(View.GONE);
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
        } else {
            layoutAuthForm.setVisibility(View.VISIBLE);
            layoutProfile.setVisibility(View.GONE);
        }
    }

    private void bindProfileData() {
        tvProfileFullName.setText(prefManager.getFullName());
        tvProfileEmail.setText(prefManager.getEmail());
        tvProfilePhone.setText(!prefManager.getPhone().isEmpty() ? prefManager.getPhone() : "Chưa cập nhật số điện thoại");
        tvProfileAddress.setText(!prefManager.getAddress().isEmpty() ? prefManager.getAddress() : "Chưa cập nhật địa chỉ nhận hàng");

        String avatarUrl = prefManager.getAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .into(ivAvatar);
        }
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
                    Map<String, Object> userMap = (Map<String, Object>) response.body().get("user");
                    saveUserDataToPref(userMap);
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

    private void handleRegister() {
        String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
        String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = (etConfirmPassword != null && etConfirmPassword.getText() != null)
                ? etConfirmPassword.getText().toString().trim() : "";

        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập đầy đủ tất cả thông tin!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), "Địa chỉ Email không đúng định dạng!", Toast.LENGTH_SHORT).show();
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

        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", fullName);
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);

        apiService.register(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && "SUCCESS".equals(response.body().get("status"))) {
                    Map<String, Object> userMap = (Map<String, Object>) response.body().get("user");
                    saveUserDataToPref(userMap);
                    checkLoginState();
                    Toast.makeText(requireContext(), "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Đăng ký thất bại. Tên đăng nhập hoặc email đã tồn tại!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleFirebaseLogin() {
        if (mAuth == null) {
            Toast.makeText(requireContext(), "Firebase chưa được khởi tạo (thiếu google-services.json)!", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Chưa có session Firebase!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> body = new HashMap<>();
        body.put("email", currentUser.getEmail());
        body.put("fullName", currentUser.getDisplayName());
        body.put("avatarUrl", currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl().toString() : "");
        body.put("firebaseUid", currentUser.getUid());

        apiService.firebaseLogin(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> userMap = (Map<String, Object>) response.body().get("user");
                    saveUserDataToPref(userMap);
                    checkLoginState();
                    Toast.makeText(requireContext(), "Đăng nhập Google thành công!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Lỗi đồng bộ Firebase: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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

    // ================= SỔ ĐỊA CHỈ (ADDRESS BOOK BOTTOM SHEET) =================
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

                // Đồng bộ địa chỉ mới chọn lên Backend MySQL
                Map<String, Object> body = new HashMap<>();
                body.put("fullName", address.getReceiverName());
                body.put("phone", address.getPhone());
                body.put("address", address.getAddressLine());
                apiService.updateProfile(prefManager.getUserId(), body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });

                bottomSheet.dismiss();
                Toast.makeText(requireContext(), "Đã chọn địa chỉ: " + address.getTag(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(UserAddress address) {
                prefManager.deleteAddress(address.getId());
                addressList.clear();
                addressList.addAll(prefManager.getAddressList());
                adapterHolder[0].notifyDataSetChanged();
                bindProfileData();
                Toast.makeText(requireContext(), "Đã xóa địa chỉ!", Toast.LENGTH_SHORT).show();
            }
        });

        rvAddresses.setAdapter(adapterHolder[0]);

        btnAddNew.setOnClickListener(v -> showAddAddressDialog(bottomSheet, adapterHolder[0], addressList));

        bottomSheet.show();
    }

    private void showAddAddressDialog(BottomSheetDialog parentSheet, AddressAdapter adapter, List<UserAddress> addressList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_address, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextInputEditText etReceiverName = dialogView.findViewById(R.id.etNewReceiverName);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etNewPhone);
        TextInputEditText etAddressLine = dialogView.findViewById(R.id.etNewAddressLine);
        RadioButton rbHome = dialogView.findViewById(R.id.rbTagHome);
        CheckBox cbSetDefault = dialogView.findViewById(R.id.cbSetDefault);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelAddAddress);
        Button btnSave = dialogView.findViewById(R.id.btnSaveNewAddress);

        etReceiverName.setText(prefManager.getFullName());
        etPhone.setText(prefManager.getPhone());

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

            UserAddress newAddr = new UserAddress(name, phone, address, tag, isDefault);
            prefManager.addAddress(newAddr);

            addressList.clear();
            addressList.addAll(prefManager.getAddressList());
            adapter.notifyDataSetChanged();
            bindProfileData();

            if (isDefault) {
                Map<String, Object> body = new HashMap<>();
                body.put("fullName", name);
                body.put("phone", phone);
                body.put("address", address);
                apiService.updateProfile(prefManager.getUserId(), body).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }

            dialog.dismiss();
            Toast.makeText(requireContext(), "Thêm địa chỉ thành công!", Toast.LENGTH_SHORT).show();
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

        prefManager.saveUser(id, username, email, fullName, phone, address, avatarUrl);
    }
}
