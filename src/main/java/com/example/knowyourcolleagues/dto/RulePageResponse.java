package com.example.knowyourcolleagues.dto;

import lombok.Data;

import java.util.List;

@Data
public class RulePageResponse {

    private List<RuleResponse> content;
    private long page;
    private long size;
    private long totalElements;
    private long totalPages;
}
