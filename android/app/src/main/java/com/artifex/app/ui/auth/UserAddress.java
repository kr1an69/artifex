package com.artifex.app.ui.auth;

import java.io.Serializable;
import java.util.UUID;

public class UserAddress implements Serializable {
    private String id;
    private String receiverName;
    private String phone;
    private String addressLine;
    private String tag; // "Nhà riêng" hoặc "Văn phòng"
    private boolean isDefault;

    public UserAddress() {
        this.id = UUID.randomUUID().toString();
    }

    public UserAddress(String receiverName, String phone, String addressLine, String tag, boolean isDefault) {
        this.id = UUID.randomUUID().toString();
        this.receiverName = receiverName;
        this.phone = phone;
        this.addressLine = addressLine;
        this.tag = tag;
        this.isDefault = isDefault;
    }

    public String getId() {
        return id != null ? id : UUID.randomUUID().toString();
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReceiverName() {
        return receiverName != null ? receiverName : "";
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getPhone() {
        return phone != null ? phone : "";
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddressLine() {
        return addressLine != null ? addressLine : "";
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getTag() {
        return (tag != null && !tag.isEmpty()) ? tag : "Nhà riêng";
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }
}
