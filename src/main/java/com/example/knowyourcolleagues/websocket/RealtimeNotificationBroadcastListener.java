package com.example.knowyourcolleagues.websocket;

import com.example.knowyourcolleagues.dto.RealtimeNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 仅在业务事务成功提交后广播通知，避免前端收到已回滚的数据。
 */
@Component
@RequiredArgsConstructor
public class RealtimeNotificationBroadcastListener {

    private final NotificationWebSocketHandler webSocketHandler;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void broadcast(RealtimeNotification notification) {
        webSocketHandler.broadcast(notification);
    }
}
