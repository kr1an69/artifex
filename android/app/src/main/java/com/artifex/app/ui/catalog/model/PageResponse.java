package com.artifex.app.ui.catalog.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PageResponse<T> {

    @SerializedName("content")
    private List<T> content;

    @SerializedName("totalElements")
    private long totalElements;

    @SerializedName("totalPages")
    private int totalPages;

    @SerializedName("size")
    private int size;

    @SerializedName("number")
    private int number;

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }
}
