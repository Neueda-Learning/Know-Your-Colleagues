package com.example.knowyourcolleagues.controller.rule;

import com.example.knowyourcolleagues.common.exception.ErrorResponse;
import com.example.knowyourcolleagues.dto.CreateRuleRequest;
import com.example.knowyourcolleagues.dto.RulePageResponse;
import com.example.knowyourcolleagues.dto.RuleResponse;
import com.example.knowyourcolleagues.dto.UpdateRuleEnabledRequest;
import com.example.knowyourcolleagues.dto.UpdateRuleRequest;
import com.example.knowyourcolleagues.enums.RuleType;
import com.example.knowyourcolleagues.enums.Severity;
import com.example.knowyourcolleagues.service.RuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
@Tag(name = "规则管理", description = "创建、查询、修改、启用和停用交易监控规则")
public class RuleController {

    private final RuleService ruleService;

    @PostMapping
    @Operation(
            summary = "创建监控规则",
            description = "创建金额阈值、交易频率、新收款人或每日限额规则。"
                    + "不同规则类型需要提供对应的参数字段。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "规则创建成功",
                    content = @Content(
                            schema = @Schema(implementation = RuleResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "规则公共字段缺失，或规则参数不符合对应类型要求",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<RuleResponse> createRule(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "规则基本信息和对应类型的参数",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = CreateRuleRequest.class
                            ),
                            examples = {
                                    @ExampleObject(
                                            name = "金额阈值规则",
                                            value = """
                                                    {
                                                      "name": "大额美元交易",
                                                      "description": "单笔美元交易超过 10000 时告警",
                                                      "type": "AMOUNT_THRESHOLD",
                                                      "severity": "HIGH",
                                                      "enabled": true,
                                                      "currency": "USD",
                                                      "thresholdAmount": 10000
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "交易频率规则",
                                            value = """
                                                    {
                                                      "name": "十分钟高频交易",
                                                      "description": "同一账户十分钟内超过五笔交易时告警",
                                                      "type": "VELOCITY",
                                                      "severity": "MEDIUM",
                                                      "enabled": true,
                                                      "transactionCount": 5,
                                                      "timeWindowMinutes": 10
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "新收款人规则",
                                            value = """
                                                    {
                                                      "name": "新收款人检测",
                                                      "description": "账户首次向某收款人交易时告警",
                                                      "type": "NEW_PAYEE",
                                                      "severity": "LOW",
                                                      "enabled": true
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "每日限额规则",
                                            value = """
                                                    {
                                                      "name": "每日美元支出限额",
                                                      "description": "每日美元借记交易累计超过 50000 时告警",
                                                      "type": "DAILY_LIMIT",
                                                      "severity": "HIGH",
                                                      "enabled": true,
                                                      "currency": "USD",
                                                      "dailyLimitAmount": 50000
                                                    }
                                                    """
                                    )
                            }
                    )
            )
            @RequestBody CreateRuleRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.createRule(request));
    }

    @GetMapping
    @Operation(
            summary = "分页查询监控规则",
            description = "按规则类型、启用状态和告警级别筛选规则；筛选条件均可选。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(
                            schema = @Schema(
                                    implementation = RulePageResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "页码、每页数量或枚举参数不合法",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<RulePageResponse> getRules(
            @Parameter(
                    description = "规则类型",
                    example = "AMOUNT_THRESHOLD"
            )
            @RequestParam(required = false) RuleType type,
            @Parameter(
                    description = "启用状态：true 表示启用，false 表示停用",
                    example = "true"
            )
            @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "规则命中后的告警级别", example = "HIGH")
            @RequestParam(required = false) Severity severity,
            @Parameter(description = "页码，从 0 开始", example = "0")
            @RequestParam(defaultValue = "0") long page,
            @Parameter(description = "每页数量，范围 1~100", example = "20")
            @RequestParam(defaultValue = "20") long size
    ) {
        return ResponseEntity.ok(
                ruleService.getRules(type, enabled, severity, page, size)
        );
    }

    @GetMapping("/{ruleId}")
    @Operation(
            summary = "查询监控规则详情",
            description = "根据规则 ID 返回规则基本信息、启用状态和规则参数。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "查询成功",
                    content = @Content(
                            schema = @Schema(implementation = RuleResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "规则 ID 不合法",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "规则不存在",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<RuleResponse> getRule(
            @Parameter(
                    description = "规则 ID，必须为正整数",
                    example = "1",
                    required = true
            )
            @PathVariable Long ruleId
    ) {
        return ResponseEntity.ok(ruleService.getRule(ruleId));
    }

    @PutMapping("/{ruleId}")
    @Operation(
            summary = "修改监控规则",
            description = "修改规则名称、说明、告警级别和参数。"
                    + "规则类型创建后不可修改，version 用于防止并发覆盖。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "规则修改成功",
                    content = @Content(
                            schema = @Schema(implementation = RuleResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "规则 ID、版本或规则参数不合法",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "规则不存在",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "规则已被其他请求修改，请重新查询后提交",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<RuleResponse> updateRule(
            @Parameter(
                    description = "规则 ID，必须为正整数",
                    example = "1",
                    required = true
            )
            @PathVariable Long ruleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "修改后的规则信息及当前 version",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = UpdateRuleRequest.class
                            ),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "name": "大额美元交易",
                                              "description": "单笔美元交易超过 20000 时告警",
                                              "severity": "HIGH",
                                              "currency": "USD",
                                              "thresholdAmount": 20000,
                                              "version": 0
                                            }
                                            """
                            )
                    )
            )
            @RequestBody UpdateRuleRequest request
    ) {
        return ResponseEntity.ok(ruleService.updateRule(ruleId, request));
    }

    @PatchMapping("/{ruleId}/enabled")
    @Operation(
            summary = "启用或停用监控规则",
            description = "修改规则的启用状态。停用后的规则不会参与新交易的评估，"
                    + "但不会删除已有告警和历史记录。"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "启用状态修改成功",
                    content = @Content(
                            schema = @Schema(implementation = RuleResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "规则 ID、enabled 或 version 不合法",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "规则不存在",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "规则已被其他请求修改，请重新查询后提交",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    public ResponseEntity<RuleResponse> updateEnabled(
            @Parameter(
                    description = "规则 ID，必须为正整数",
                    example = "1",
                    required = true
            )
            @PathVariable Long ruleId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "目标启用状态及当前 version",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation =
                                            UpdateRuleEnabledRequest.class
                            ),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "enabled": false,
                                              "version": 0
                                            }
                                            """
                            )
                    )
            )
            @RequestBody UpdateRuleEnabledRequest request
    ) {
        return ResponseEntity.ok(
                ruleService.updateEnabled(ruleId, request)
        );
    }
}
