package com.example.knowyourcolleagues.controller.alert;

import com.example.knowyourcolleagues.common.exception.ErrorResponse;
import com.example.knowyourcolleagues.dto.AlertDetailResponse;
import com.example.knowyourcolleagues.dto.AlertHistoryResponse;
import com.example.knowyourcolleagues.dto.AlertPageResponse;
import com.example.knowyourcolleagues.dto.UpdateAlertStatusRequest;
import com.example.knowyourcolleagues.enums.AlertStatus;
import com.example.knowyourcolleagues.enums.Severity;
import com.example.knowyourcolleagues.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Tag(name = "告警管理", description = "查询告警、流转告警状态以及查看状态变更历史")
public class AlertController {

    private final AlertService alertService;

    @Operation(
            summary = "分页查询告警",
            description = "按状态、严重级别和账户编号筛选告警；筛选条件均可选。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(schema = @Schema(implementation = AlertPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "查询参数不合法",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<AlertPageResponse> getAlerts(
            @Parameter(description = "告警状态", example = "OPEN")
            @RequestParam(required = false) AlertStatus status,
            @Parameter(description = "严重级别", example = "HIGH")
            @RequestParam(required = false) Severity severity,
            @Parameter(description = "账户编号", example = "ACC-001")
            @RequestParam(required = false) String accountId,
            @Parameter(description = "页码，从 0 开始", example = "0")
            @RequestParam(defaultValue = "0") long page,
            @Parameter(description = "每页数量", example = "20")
            @RequestParam(defaultValue = "20") long size
    ) {
        return ResponseEntity.ok(
                alertService.getAlerts(status, severity, accountId, page, size)
        );
    }

    @Operation(summary = "查询告警详情", description = "根据告警 ID 返回告警详情及状态历史。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(
                    responseCode = "404",
                    description = "告警不存在",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{alertId}")
    public ResponseEntity<AlertDetailResponse> getAlert(
            @Parameter(description = "告警 ID", example = "1", required = true)
            @PathVariable Long alertId
    ) {
        return ResponseEntity.ok(alertService.getAlert(alertId));
    }

    @Operation(
            summary = "更新告警状态",
            description = "按照 OPEN → ACKNOWLEDGED → INVESTIGATING → CLOSED 的流程更新状态，"
                    + "也可以按业务规则将告警标记为 DISMISSED。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "状态更新成功"),
            @ApiResponse(
                    responseCode = "400",
                    description = "请求参数不合法",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "告警不存在",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "状态流转不合法或数据已被其他请求更新",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PatchMapping("/{alertId}/status")
    public ResponseEntity<AlertDetailResponse> updateStatus(
            @Parameter(description = "告警 ID", example = "1", required = true)
            @PathVariable Long alertId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "目标状态和本次操作说明",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = UpdateAlertStatusRequest.class),
                            examples = @ExampleObject(
                                    value = "{\"targetStatus\":\"ACKNOWLEDGED\",\"notes\":\"已由操作员确认\"}"
                            )
                    )
            )
            @RequestBody UpdateAlertStatusRequest request
    ) {
        return ResponseEntity.ok(alertService.updateStatus(alertId, request));
    }

    @Operation(summary = "查询告警状态历史", description = "按发生时间返回指定告警的状态变更记录。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(
                    responseCode = "404",
                    description = "告警不存在",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{alertId}/history")
    public ResponseEntity<List<AlertHistoryResponse>> getHistory(
            @Parameter(description = "告警 ID", example = "1", required = true)
            @PathVariable Long alertId
    ) {
        return ResponseEntity.ok(alertService.getHistory(alertId));
    }
}
