package com.example.knowyourcolleagues.websocket;

import com.example.knowyourcolleagues.dto.AlertResponse;
import com.example.knowyourcolleagues.dto.NotificationAction;
import com.example.knowyourcolleagues.dto.RealtimeNotification;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.enums.NotificationLevel;
import com.example.knowyourcolleagues.enums.NotificationTargetType;
import com.example.knowyourcolleagues.enums.NotificationType;
import com.example.knowyourcolleagues.enums.Severity;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 将业务变化转换为统一的实时通知领域事件。
 */
@Component
@RequiredArgsConstructor
public class RealtimeNotificationPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishTransactionStatusChanged(
            TransactionResponse transaction
    ) {
        RealtimeNotification notification = baseNotification();
        notification.setType(NotificationType.TRANSACTION_STATUS_CHANGED);
        notification.setLevel(
                transaction.getStatus() == TransactionStatus.NORMAL
                        ? NotificationLevel.SUCCESS
                        : NotificationLevel.WARNING
        );
        notification.setTitle("Transaction status updated");
        notification.setMessage(
                "Transaction " + displayTransaction(transaction)
                        + " has been marked as "
                        + statusLabel(transaction.getStatus())
                        + "."
        );
        notification.setTransactionId(transaction.getId());
        notification.setTransactionRef(transaction.getTransactionRef());
        notification.setTransactionStatus(transaction.getStatus());
        notification.setAccountId(transaction.getAccountId());
        notification.setAction(action(
                NotificationTargetType.TRANSACTION,
                transaction.getId(),
                "View transaction"
        ));
        applicationEventPublisher.publishEvent(notification);
    }

    public void publishAlertCreated(AlertResponse alert) {
        RealtimeNotification notification = baseNotification();
        notification.setType(NotificationType.ALERT_CREATED);
        notification.setLevel(alertLevel(alert.getSeverity()));
        notification.setTitle("New " + severityLabel(alert.getSeverity())
                + " alert");
        notification.setMessage(
                "Account " + alert.getAccountId()
                        + " triggered rule \"" + alert.getRuleName()
                        + "\". Please review this alert promptly."
        );
        notification.setTransactionId(alert.getTriggerTransactionId());
        notification.setAlertId(alert.getId());
        notification.setAlertSeverity(alert.getSeverity());
        notification.setRuleName(alert.getRuleName());
        notification.setAccountId(alert.getAccountId());
        notification.setAction(action(
                NotificationTargetType.ALERT,
                alert.getId(),
                "Review alert"
        ));
        applicationEventPublisher.publishEvent(notification);
    }

    private RealtimeNotification baseNotification() {
        RealtimeNotification notification = new RealtimeNotification();
        notification.setId(UUID.randomUUID());
        notification.setOccurredAt(Instant.now());
        return notification;
    }

    private NotificationAction action(
            NotificationTargetType targetType,
            Long targetId,
            String label
    ) {
        NotificationAction action = new NotificationAction();
        action.setTargetType(targetType);
        action.setTargetId(targetId);
        action.setLabel(label);
        return action;
    }

    private String displayTransaction(TransactionResponse transaction) {
        return transaction.getTransactionRef() == null
                ? "#" + transaction.getId()
                : transaction.getTransactionRef();
    }

    private String statusLabel(TransactionStatus status) {
        return status == TransactionStatus.NORMAL ? "NORMAL" : "ABNORMAL";
    }

    private NotificationLevel alertLevel(Severity severity) {
        return switch (severity) {
            case HIGH -> NotificationLevel.CRITICAL;
            case MEDIUM -> NotificationLevel.WARNING;
            case LOW -> NotificationLevel.INFO;
        };
    }

    private String severityLabel(Severity severity) {
        return switch (severity) {
            case HIGH -> "high-risk";
            case MEDIUM -> "medium-risk";
            case LOW -> "low-risk";
        };
    }
}
