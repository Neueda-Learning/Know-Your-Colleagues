package com.example.knowyourcolleagues.websocket;

import com.example.knowyourcolleagues.dto.AlertResponse;
import com.example.knowyourcolleagues.dto.RealtimeNotification;
import com.example.knowyourcolleagues.dto.TransactionResponse;
import com.example.knowyourcolleagues.enums.NotificationLevel;
import com.example.knowyourcolleagues.enums.NotificationTargetType;
import com.example.knowyourcolleagues.enums.NotificationType;
import com.example.knowyourcolleagues.enums.Severity;
import com.example.knowyourcolleagues.enums.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class RealtimeNotificationPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private RealtimeNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        publisher = new RealtimeNotificationPublisher(
                applicationEventPublisher
        );
    }

    @Test
    void shouldCreateNavigableNormalTransactionNotification() {
        TransactionResponse transaction = new TransactionResponse();
        transaction.setId(1001L);
        transaction.setTransactionRef("TXN-TEST-1001");
        transaction.setAccountId("ACC-001");
        transaction.setStatus(TransactionStatus.NORMAL);

        publisher.publishTransactionStatusChanged(transaction);

        RealtimeNotification notification = captureNotification();
        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getOccurredAt()).isNotNull();
        assertThat(notification.getType())
                .isEqualTo(NotificationType.TRANSACTION_STATUS_CHANGED);
        assertThat(notification.getLevel())
                .isEqualTo(NotificationLevel.SUCCESS);
        assertThat(notification.getMessage())
                .contains("TXN-TEST-1001")
                .contains("NORMAL");
        assertThat(notification.getAction().getTargetType())
                .isEqualTo(NotificationTargetType.TRANSACTION);
        assertThat(notification.getAction().getTargetId()).isEqualTo(1001L);
    }

    @Test
    void shouldCreateCriticalAlertActionNotification() {
        AlertResponse alert = new AlertResponse();
        alert.setId(2001L);
        alert.setTriggerTransactionId(1001L);
        alert.setAccountId("ACC-001");
        alert.setRuleName("High-value transaction");
        alert.setSeverity(Severity.HIGH);

        publisher.publishAlertCreated(alert);

        RealtimeNotification notification = captureNotification();
        assertThat(notification.getType())
                .isEqualTo(NotificationType.ALERT_CREATED);
        assertThat(notification.getLevel())
                .isEqualTo(NotificationLevel.CRITICAL);
        assertThat(notification.getMessage())
                .contains("ACC-001")
                .contains("High-value transaction")
                .contains("review this alert promptly");
        assertThat(notification.getAlertId()).isEqualTo(2001L);
        assertThat(notification.getAction().getTargetType())
                .isEqualTo(NotificationTargetType.ALERT);
        assertThat(notification.getAction().getTargetId()).isEqualTo(2001L);
    }

    private RealtimeNotification captureNotification() {
        ArgumentCaptor<Object> eventCaptor =
                ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
                .isInstanceOf(RealtimeNotification.class);
        return (RealtimeNotification) eventCaptor.getValue();
    }
}
