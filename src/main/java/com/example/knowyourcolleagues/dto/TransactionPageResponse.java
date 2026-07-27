package com.example.knowyourcolleagues.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 交易分页查询响应。
 */
@Data
@Schema(description = "交易分页查询结果")
public class TransactionPageResponse {

    @Schema(description = "当前页交易数据")
    private List<TransactionResponse> content;

    @Schema(description = "当前页码，从 0 开始", example = "0")
    private long page;

    @Schema(description = "每页数量", example = "20")
    private long size;

    @Schema(description = "符合条件的总记录数", example = "45")
    private long totalElements;

    @Schema(description = "总页数", example = "3")
    private long totalPages;
}
