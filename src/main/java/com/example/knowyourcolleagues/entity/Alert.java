package com.example.knowyourcolleagues.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.example.knowyourcolleagues.enums.AlertStatus;
import com.example.knowyourcolleagues.enums.Severity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alerts")
public class Alert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;
    private Long triggerTransactionId;
    private String accountId;
    private String ruleName;
    private Severity severity;
    private AlertStatus status;
    private String title;
    private String description;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime investigatingAt;
    private LocalDateTime closedAt;
    private LocalDateTime dismissedAt;
    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
