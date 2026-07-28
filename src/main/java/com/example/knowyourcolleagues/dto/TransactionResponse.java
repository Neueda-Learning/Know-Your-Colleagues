package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易信息响应。
 */
@Data
@Schema(description = "交易信息")
public class TransactionResponse {

    @Schema(description = "交易主键", example = "1")
    private Long id;

    @Schema(
            description = "后端生成的唯一交易号",
            example = "TXN-20260727143000123-A1B2C3D4E5F60718293A"
    )
    private String transactionRef;

    @Schema(description = "账户编号", example = "ACC-001")
    private String accountId;

    @Schema(description = "收款人或交易对手编号", example = "PAYEE-001")
    private String payeeId;

    @Schema(description = "交易金额", example = "15000.00")
    private BigDecimal amount;

    @Schema(description = "币种", example = "USD")
    private String currency;

    @Schema(description = "交易类型", example = "DEBIT")
    private TransactionType transactionType;

    @Schema(description = "交易状态", example = "PENDING")
    private TransactionStatus status;

    @Schema(description = "交易描述", example = "Supplier payment")
    private String description;

    @Schema(description = "交易发生时间", example = "2026-07-27T14:30:00")
    private LocalDateTime transactionTime;

    @Schema(description = "记录创建时间", example = "2026-07-27T14:30:01")
    private LocalDateTime createdAt;

    @Schema(description = "记录更新时间", example = "2026-07-27T14:30:01")
    private LocalDateTime updatedAt;
}
