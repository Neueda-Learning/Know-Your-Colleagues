package com.example.knowyourcolleagues.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "启用或停用规则")
public class UpdateRuleEnabledRequest {

    private Boolean enabled;
    private Integer version;
}
