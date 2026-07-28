package com.example.knowyourcolleagues.service;

import com.example.knowyourcolleagues.dto.dashboard.DashboardSnapshot;
import com.example.knowyourcolleagues.enums.DashboardUpdateType;

/**
 * 构建仪表盘完整或分区数据快照。
 */
public interface DashboardService {
    DashboardSnapshot getSnapshot(DashboardUpdateType updateType);
}
