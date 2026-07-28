package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易列表的筛选和分页参数。
 */
@Data
@Schema(description = "交易分页查询参数")
public class TransactionQueryRequest {

    @Size(max = 64)
    @Schema(description = "账户编号", example = "ACC-001")
    private String accountId;

    @Size(max = 64)
    @Schema(description = "收款人或交易对手编号", example = "PAYEE-001")
    private String payeeId;

    @DecimalMin(value = "0.00")
    @Schema(description = "最小交易金额，包含边界", example = "100.00")
    private BigDecimal minAmount;

    @DecimalMin(value = "0.00")
    @Schema(description = "最大交易金额，包含边界", example = "20000.00")
    private BigDecimal maxAmount;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "交易时间起点，包含边界", example = "2026-07-01T00:00:00")
    private LocalDateTime transactionTimeStart;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Schema(description = "交易时间终点，包含边界", example = "2026-07-31T23:59:59")
    private LocalDateTime transactionTimeEnd;

    @Schema(description = "交易状态", example = "NORMAL")
    private TransactionStatus status;

    @Min(0)
    @Schema(description = "页码，从 0 开始", example = "0", defaultValue = "0")
    private long page = 0;

    @Min(1)
    @Max(100)
    @Schema(description = "每页数量，最大 100", example = "20", defaultValue = "20")
    private long size = 20;
}
