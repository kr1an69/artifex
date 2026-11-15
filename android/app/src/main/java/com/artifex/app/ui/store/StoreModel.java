package com.artifex.app.ui.store;

import com.google.gson.annotations.SerializedName;

/**
 * POJO Model đại diện cho Showroom Artifex (Phụ trách: Bạn 5).
 * Ánh xạ từ API GET /api/stores của StoreController và phục vụ tính toán khoảng cách đa luồng.
 */
public class StoreModel {

    @SerializedName("id")
    private Long id;

    @SerializedName("name")
    private String name;

    @SerializedName("address")
    private String address;

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("phone")
    private String phone;

    @SerializedName(value = "openingHours", alternate = {"opening_hours"})
    private String openingHours;

    // Thuộc tính phục vụ tính toán khoảng cách GPS trên Worker Thread (ExecutorService)
    private transient double distanceKm = -1.0;

    public StoreModel() {
    }

    public StoreModel(Long id, String name, String address, Double latitude, Double longitude, String phone, String openingHours) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.openingHours = openingHours;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Artifex Showroom";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address != null ? address : "";
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLatitude() {
        return latitude != null ? latitude : 0.0;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude != null ? longitude : 0.0;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getPhone() {
        return phone != null ? phone : "";
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOpeningHours() {
        return (openingHours != null && !openingHours.trim().isEmpty())
                ? openingHours
                : "08:00 - 21:30 (T2 - CN)";
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }
}
