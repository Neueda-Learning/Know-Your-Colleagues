package com.example.knowyourcolleagues.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "统一错误响应")
public record ErrorResponse(
        @Schema(description = "业务错误码", example = "RESOURCE_NOT_FOUND")
        String code,
        @Schema(description = "错误说明", example = "Requested resource was not found")
        String message,
        @Schema(description = "错误发生时间", example = "2026-07-27T08:30:00Z")
        Instant timestamp,
        @Schema(description = "发生错误的请求路径", example = "/api/transactions/1")
        String path
) {
}
