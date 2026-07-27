package com.example.knowyourcolleagues.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.knowyourcolleagues.enums.AlertStatus;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("alert_history")
public class AlertHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alertId;
    private AlertStatus fromStatus;
    private AlertStatus toStatus;
    private String notes;
    private Instant changedAt;
}
