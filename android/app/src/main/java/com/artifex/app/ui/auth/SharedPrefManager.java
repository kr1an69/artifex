package com.artifex.app.ui.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SharedPrefManager {

    private static final String SHARED_PREF_NAME = "artifex_user_pref";
    private static final String KEY_USER_ID = "key_user_id";
    private static final String KEY_USERNAME = "key_username";
    private static final String KEY_EMAIL = "key_email";
    private static final String KEY_FULL_NAME = "key_full_name";
    private static final String KEY_PHONE = "key_phone";
    private static final String KEY_ADDRESS = "key_address";
    private static final String KEY_AVATAR_URL = "key_avatar_url";
    private static final String KEY_IS_LOGGED_IN = "key_is_logged_in";
    private static final String KEY_ADDRESS_LIST = "key_address_list";

    private static SharedPrefManager instance;
    private final Context ctx;
    private final Gson gson;

    private SharedPrefManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.gson = new Gson();
    }

    public static synchronized SharedPrefManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPrefManager(context);
        }
        return instance;
    }

    public void saveUser(Long id, String username, String email, String fullName, String phone, String address, String avatarUrl) {
        SharedPreferences sp = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong(KEY_USER_ID, id != null ? id : -1);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putString(KEY_PHONE, phone);
        editor.putString(KEY_ADDRESS, address);
        editor.putString(KEY_AVATAR_URL, avatarUrl);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();

        // Khởi tạo một địa chỉ mặc định trong sổ địa chỉ nếu có dữ liệu
        if (address != null && !address.trim().isEmpty()) {
            List<UserAddress> currentList = getAddressList();
            if (currentList.isEmpty()) {
                UserAddress defaultAddr = new UserAddress(fullName, phone, address, "Nhà riêng", true);
                addAddress(defaultAddr);
            }
        }
    }

    public boolean isLoggedIn() {
        return ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public Long getUserId() {
        return ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getLong(KEY_USER_ID, 1);
    }

    public String getFullName() {
        return ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getString(KEY_FULL_NAME, "Khách hàng");
    }

    public String getEmail() {
        return ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getString(KEY_EMAIL, "");
    }

    public String getPhone() {
        return ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getString(KEY_PHONE, "");
    }

    public String getAddress() {
        return ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getString(KEY_ADDRESS, "");
    }

    public String getAvatarUrl() {
        return ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getString(KEY_AVATAR_URL, "");
    }

    public void updateAvatarUrl(String url) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_AVATAR_URL, url);
        editor.apply();
    }

    public void updateProfile(String fullName, String phone, String address) {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_FULL_NAME, fullName);
        editor.putString(KEY_PHONE, phone);
        editor.putString(KEY_ADDRESS, address);
        editor.apply();
    }

    // ================= SỔ ĐỊA CHỈ (ADDRESS BOOK) =================
    public List<UserAddress> getAddressList() {
        String json = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).getString(KEY_ADDRESS_LIST, null);
        if (json == null || json.trim().isEmpty()) {
            List<UserAddress> list = new ArrayList<>();
            String currentAddr = getAddress();
            if (!currentAddr.isEmpty()) {
                list.add(new UserAddress(getFullName(), getPhone(), currentAddr, "Nhà riêng", true));
            }
            return list;
        }
        Type type = new TypeToken<List<UserAddress>>() {}.getType();
        return gson.fromJson(json, type);
    }

    public void saveAddressList(List<UserAddress> list) {
        String json = gson.toJson(list);
        SharedPreferences.Editor editor = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.putString(KEY_ADDRESS_LIST, json);
        editor.apply();
    }

    public void addAddress(UserAddress newAddress) {
        List<UserAddress> list = getAddressList();
        if (list.isEmpty() || newAddress.isDefault()) {
            for (UserAddress a : list) {
                a.setDefault(false);
            }
            newAddress.setDefault(true);
            updateProfile(newAddress.getReceiverName(), newAddress.getPhone(), newAddress.getAddressLine());
        }
        list.add(0, newAddress);
        saveAddressList(list);
    }

    public void setDefaultAddress(String addressId) {
        List<UserAddress> list = getAddressList();
        UserAddress defaultAddress = null;
        for (UserAddress a : list) {
            if (a.getId().equals(addressId)) {
                a.setDefault(true);
                defaultAddress = a;
            } else {
                a.setDefault(false);
            }
        }
        if (defaultAddress != null) {
            updateProfile(defaultAddress.getReceiverName(), defaultAddress.getPhone(), defaultAddress.getAddressLine());
        }
        saveAddressList(list);
    }

    public void deleteAddress(String addressId) {
        List<UserAddress> list = getAddressList();
        boolean removedDefault = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(addressId)) {
                if (list.get(i).isDefault()) {
                    removedDefault = true;
                }
                list.remove(i);
                break;
            }
        }
        if (removedDefault && !list.isEmpty()) {
            list.get(0).setDefault(true);
            updateProfile(list.get(0).getReceiverName(), list.get(0).getPhone(), list.get(0).getAddressLine());
        }
        saveAddressList(list);
    }

    public void clear() {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
    }
}
