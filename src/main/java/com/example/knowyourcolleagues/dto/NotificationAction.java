package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.NotificationTargetType;
import lombok.Data;

/**
 * 通知点击后的前端导航信息。
 */
@Data
public class NotificationAction {
    private NotificationTargetType targetType;
    private Long targetId;
    private String label;
}
