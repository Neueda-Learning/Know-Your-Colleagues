package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.AlertStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "告警状态更新请求")
public class UpdateAlertStatusRequest {

    @Schema(description = "希望流转到的目标状态", example = "ACKNOWLEDGED", requiredMode = Schema.RequiredMode.REQUIRED)
    private AlertStatus targetStatus;

    @Schema(description = "状态变更说明；关闭或驳回告警时建议填写", example = "已由操作员确认")
    private String notes;
}
