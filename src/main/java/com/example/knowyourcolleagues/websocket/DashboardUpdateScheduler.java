package com.example.knowyourcolleagues.websocket;

import com.example.knowyourcolleagues.enums.DashboardUpdateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按数据变化速度分层广播仪表盘更新，避免所有聚合查询高频执行。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardUpdateScheduler {

    private final DashboardWebSocketHandler dashboardWebSocketHandler;

    @Scheduled(
            fixedDelayString = "${dashboard.websocket.operations-interval-ms:5000}",
            initialDelayString = "${dashboard.websocket.operations-interval-ms:5000}"
    )
    public void broadcastOperations() {
        broadcastSafely(DashboardUpdateType.OPERATIONS);
    }

    @Scheduled(
            fixedDelayString = "${dashboard.websocket.transactions-interval-ms:15000}",
            initialDelayString = "${dashboard.websocket.transactions-interval-ms:15000}"
    )
    public void broadcastTransactions() {
        broadcastSafely(DashboardUpdateType.TRANSACTIONS);
    }

    @Scheduled(
            fixedDelayString = "${dashboard.websocket.sla-interval-ms:60000}",
            initialDelayString = "${dashboard.websocket.sla-interval-ms:60000}"
    )
    public void broadcastSla() {
        broadcastSafely(DashboardUpdateType.SLA);
    }

    private void broadcastSafely(DashboardUpdateType updateType) {
        try {
            dashboardWebSocketHandler.broadcast(updateType);
        } catch (RuntimeException exception) {
            log.error(
                    "Unable to broadcast dashboard update: type={}",
                    updateType,
                    exception
            );
        }
    }
}
