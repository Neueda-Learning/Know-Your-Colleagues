package com.example.knowyourcolleagues.websocket;

import com.example.knowyourcolleagues.dto.dashboard.DashboardSnapshot;
import com.example.knowyourcolleagues.dto.dashboard.DashboardUpdateMessage;
import com.example.knowyourcolleagues.enums.DashboardUpdateType;
import com.example.knowyourcolleagues.service.DashboardService;
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
 * 仪表盘专用 WebSocket，会在连接时发送完整快照并支持分区广播。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final DashboardService dashboardService;
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
                "Dashboard WebSocket connected: sessionId={}, clients={}",
                session.getId(),
                sessions.size()
        );
        sendUpdate(concurrentSession, DashboardUpdateType.FULL);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) {
        if ("REFRESH".equalsIgnoreCase(message.getPayload().trim())) {
            WebSocketSession registered = sessions.get(session.getId());
            if (registered != null) {
                sendUpdate(registered, DashboardUpdateType.FULL);
            }
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        sessions.remove(session.getId());
        log.info(
                "Dashboard WebSocket disconnected: sessionId={}, clients={}",
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
                "Dashboard WebSocket transport error: sessionId={}",
                session.getId(),
                exception
        );
        closeQuietly(session);
    }

    public void broadcast(DashboardUpdateType updateType) {
        if (sessions.isEmpty()) {
            return;
        }
        DashboardUpdateMessage update = createUpdate(updateType);
        TextMessage message = new TextMessage(serialize(update));
        sessions.forEach((sessionId, session) ->
                sendMessage(sessionId, session, message, updateType)
        );
    }

    int connectedClientCount() {
        return sessions.size();
    }

    private void sendUpdate(
            WebSocketSession session,
            DashboardUpdateType updateType
    ) {
        DashboardUpdateMessage update = createUpdate(updateType);
        try {
            session.sendMessage(new TextMessage(serialize(update)));
        } catch (IOException | RuntimeException exception) {
            sessions.remove(session.getId());
            log.warn(
                    "Failed to send dashboard update: sessionId={}, type={}",
                    session.getId(),
                    updateType,
                    exception
            );
            closeQuietly(session);
        }
    }

    private void sendMessage(
            String sessionId,
            WebSocketSession session,
            TextMessage message,
            DashboardUpdateType updateType
    ) {
        if (!session.isOpen()) {
            sessions.remove(sessionId);
            return;
        }
        try {
            session.sendMessage(message);
        } catch (IOException | RuntimeException exception) {
            sessions.remove(sessionId);
            log.warn(
                    "Failed to broadcast dashboard update: sessionId={}, type={}",
                    sessionId,
                    updateType,
                    exception
            );
            closeQuietly(session);
        }
    }

    private DashboardUpdateMessage createUpdate(
            DashboardUpdateType updateType
    ) {
        DashboardSnapshot snapshot = dashboardService.getSnapshot(updateType);
        DashboardUpdateMessage update = new DashboardUpdateMessage();
        update.setType(updateType);
        update.setGeneratedAt(snapshot.getGeneratedAt());
        update.setData(snapshot);
        return update;
    }

    private String serialize(DashboardUpdateMessage update) {
        try {
            return objectMapper.writeValueAsString(update);
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Unable to serialize dashboard update",
                    exception
            );
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // The broken session has already been removed.
        }
    }
}
