package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.TransactionStatus;
import com.example.knowyourcolleagues.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建单笔交易的请求参数。
 */
@Data
@Schema(description = "创建单笔交易请求")
public class CreateTransactionRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "发起交易的账户编号", example = "ACC-001")
    private String accountId;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "收款人或交易对手编号", example = "PAYEE-001")
    private String payeeId;

    @NotNull
    @DecimalMin(value = "0.01")
    @Schema(description = "交易金额，必须大于 0", example = "15000.00")
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "[A-Za-z]{3}")
    @Schema(description = "ISO 4217 三位币种代码", example = "USD")
    private String currency;

    @NotNull
    @Schema(description = "交易类型", example = "DEBIT")
    private TransactionType transactionType;

    @Schema(description = "交易状态；不传时默认为 COMPLETED", example = "COMPLETED")
    private TransactionStatus status;

    @Size(max = 500)
    @Schema(description = "交易描述或附言", example = "Supplier payment")
    private String description;

    @Schema(
            description = "交易发生时间，按 UTC 解释；不传时使用当前时间",
            example = "2026-07-27T14:30:00"
    )
    private LocalDateTime transactionTime;
}
