package com.example.knowyourcolleagues.dto;

import com.example.knowyourcolleagues.enums.NotificationLevel;
import com.example.knowyourcolleagues.enums.NotificationType;
import com.example.knowyourcolleagues.enums.Severity;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 通过 WebSocket 广播给当前操作员的统一通知消息。
 */
@Data
public class RealtimeNotification {
    private UUID id;
    private NotificationType type;
    private NotificationLevel level;
    private String title;
    private String message;
    private Instant occurredAt;
    private NotificationAction action;

    private Long transactionId;
    private String transactionRef;
    private TransactionStatus transactionStatus;

    private Long alertId;
    private Severity alertSeverity;
    private String ruleName;
    private String accountId;
}
