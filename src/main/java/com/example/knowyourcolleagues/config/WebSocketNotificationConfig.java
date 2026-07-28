package com.example.knowyourcolleagues.config;

import com.example.knowyourcolleagues.websocket.DashboardWebSocketHandler;
import com.example.knowyourcolleagues.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 浏览器实时通知 WebSocket 配置。
 */
@Configuration
@EnableWebSocket
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketNotificationConfig implements WebSocketConfigurer {

    public static final String NOTIFICATION_ENDPOINT = "/ws/notifications";
    public static final String DASHBOARD_ENDPOINT = "/ws/dashboard";

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final DashboardWebSocketHandler dashboardWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(
                        notificationWebSocketHandler,
                        NOTIFICATION_ENDPOINT
                )
                .setAllowedOriginPatterns("*");
        registry.addHandler(dashboardWebSocketHandler, DASHBOARD_ENDPOINT)
                .setAllowedOriginPatterns("*");
    }
}
