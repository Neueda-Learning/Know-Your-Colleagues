package com.example.knowyourcolleagues.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.knowyourcolleagues.alert.enums.AlertStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_history")
public class AlertHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alertId;
    private AlertStatus fromStatus;
    private AlertStatus toStatus;
    private String notes;
    private LocalDateTime changedAt;
}
