package com.example.knowyourcolleagues.alert.dto;

import lombok.Data;

import java.util.List;

@Data
public class AlertPageResponse {

    private List<AlertResponse> content;
    private long page;
    private long size;
    private long totalElements;
    private long totalPages;
}
