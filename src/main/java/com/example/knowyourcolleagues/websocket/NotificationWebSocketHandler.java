package com.example.knowyourcolleagues.websocket;

import com.example.knowyourcolleagues.dto.RealtimeNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护浏览器 WebSocket 会话，并向所有在线操作员广播通知。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 256 * 1024;

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession concurrentSession =
                new ConcurrentWebSocketSessionDecorator(
                        session,
                        SEND_TIME_LIMIT_MILLIS,
                        BUFFER_SIZE_LIMIT_BYTES
                );
        sessions.put(session.getId(), concurrentSession);
        log.info(
                "Notification WebSocket connected: sessionId={}, clients={}",
                session.getId(),
                sessions.size()
        );
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        sessions.remove(session.getId());
        log.info(
                "Notification WebSocket disconnected: sessionId={}, clients={}",
                session.getId(),
                sessions.size()
        );
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) {
        sessions.remove(session.getId());
        log.warn(
                "Notification WebSocket transport error: sessionId={}",
                session.getId(),
                exception
        );
        closeQuietly(session);
    }

    public void broadcast(RealtimeNotification notification) {
        String payload = serialize(notification);
        TextMessage message = new TextMessage(payload);
        sessions.forEach((sessionId, session) -> {
            if (!session.isOpen()) {
                sessions.remove(sessionId);
                return;
            }
            try {
                session.sendMessage(message);
            } catch (IOException | RuntimeException exception) {
                sessions.remove(sessionId);
                log.warn(
                        "Failed to send notification: sessionId={}, "
                                + "notificationId={}",
                        sessionId,
                        notification.getId(),
                        exception
                );
                closeQuietly(session);
            }
        });
    }

    int connectedClientCount() {
        return sessions.size();
    }

    private String serialize(RealtimeNotification notification) {
        try {
            return objectMapper.writeValueAsString(notification);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Unable to serialize real-time notification",
                    exception
            );
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // The broken session has already been removed from the registry.
        }
    }
}
