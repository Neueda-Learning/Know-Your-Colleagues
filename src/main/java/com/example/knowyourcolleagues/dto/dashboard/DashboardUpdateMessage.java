package com.example.knowyourcolleagues.dto.dashboard;

import com.example.knowyourcolleagues.enums.DashboardUpdateType;
import lombok.Data;

import java.time.Instant;

/**
 * 仪表盘专用 WebSocket 消息。
 */
@Data
public class DashboardUpdateMessage {
    private DashboardUpdateType type;
    private Instant generatedAt;
    private DashboardSnapshot data;
}
