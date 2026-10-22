package com.artifex.app.ui.auth;

import android.content.Context;
import android.content.SharedPreferences;

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

    private static SharedPrefManager instance;
    private final Context ctx;

    private SharedPrefManager(Context context) {
        this.ctx = context.getApplicationContext();
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

    public void clear() {
        SharedPreferences.Editor editor = ctx.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
    }
}