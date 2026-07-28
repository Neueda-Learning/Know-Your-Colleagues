package com.example.knowyourcolleagues.controller.transaction;

import com.example.knowyourcolleagues.common.exception.ErrorResponse;
import com.example.knowyourcolleagues.dto.CreateTransactionRequest;
import com.example.knowyourcolleagues.dto.TransactionPageResponse;
import com.example.knowyourcolleagues.dto.TransactionQueryRequest;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 交易查询与创建接口。
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "交易管理", description = "创建交易、分页查询交易以及查看交易详情")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "创建单笔交易",
            description = "同步保存一笔交易；数据库事务提交成功后，将包含该交易的列表事件发布到 RabbitMQ。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "交易创建成功"),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求参数不合法",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(responseCode = "500", description = "交易号生成失败")
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "待创建的交易信息",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateTransactionRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "accountId": "ACC-001",
                                      "payeeId": "PAYEE-001",
                                      "amount": 15000.00,
                                      "currency": "USD",
                                      "transactionType": "DEBIT",
                                      "status": "COMPLETED",
                                      "description": "Supplier payment",
                                      "transactionTime": "2026-07-27T14:30:00"
                                    }
                                    """)
                    )
            )
            @Valid @RequestBody CreateTransactionRequest request
    ) {
        TransactionResponse transaction =
                transactionService.createTransaction(request);
        return ResponseEntity
                .created(URI.create("/api/transactions/" + transaction.getId()))
                .body(transaction);
    }

    @Operation(
            summary = "分页查询交易",
            description = "所有筛选条件均为可选；金额和交易时间范围均包含起止边界。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(
                    responseCode = "400",
                    description = "筛选或分页参数不合法",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<TransactionPageResponse> getTransactions(
            @Valid @ParameterObject @ModelAttribute TransactionQueryRequest query
    ) {
        return ResponseEntity.ok(transactionService.getTransactions(query));
    }

    @Operation(summary = "查询交易详情", description = "根据交易主键查询完整交易信息。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(
                    responseCode = "400",
                    description = "交易主键不合法",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "交易不存在",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @Parameter(description = "交易主键", example = "1", required = true)
            @PathVariable Long transactionId
    ) {
        return ResponseEntity.ok(
                transactionService.getTransaction(transactionId)
        );
    }
}
