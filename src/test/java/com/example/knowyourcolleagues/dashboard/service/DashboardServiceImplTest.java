package com.example.knowyourcolleagues.dashboard.service;

import com.example.knowyourcolleagues.dto.dashboard.DashboardCategoryCount;
import com.example.knowyourcolleagues.dto.dashboard.DashboardOperationalSummaryRow;
import com.example.knowyourcolleagues.dto.dashboard.DashboardSnapshot;
import com.example.knowyourcolleagues.dto.dashboard.DashboardTransactionPoint;
import com.example.knowyourcolleagues.enums.DashboardUpdateType;
import com.example.knowyourcolleagues.mapper.DashboardMapper;
import com.example.knowyourcolleagues.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    @Mock
    private DashboardMapper dashboardMapper;

    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dashboardService = new DashboardServiceImpl(dashboardMapper);
    }

    @Test
    void shouldBuildCompleteNormalizedSnapshot() {
        DashboardOperationalSummaryRow summaryRow =
                new DashboardOperationalSummaryRow();
        summaryRow.setOpenAlerts(12L);
        summaryRow.setAcknowledgedAlerts(8L);
        summaryRow.setTotalAlertsToday(30L);
        summaryRow.setTotalAlertsYesterday(20L);

        DashboardTransactionPoint hourTwo =
                new DashboardTransactionPoint();
        hourTwo.setHourOfDay(2);
        hourTwo.setTransactionCount(7L);

        when(dashboardMapper.selectOperationalSummary(any(), any(), any()))
                .thenReturn(summaryRow);
        when(dashboardMapper.selectAlertsBySeverity(any(), any()))
                .thenReturn(List.of(
                        new DashboardCategoryCount("HIGH", 5L)
                ));
        when(dashboardMapper.selectAlertsByStatus(any(), any()))
                .thenReturn(List.of(
                        new DashboardCategoryCount("OPEN", 5L)
                ));
        when(dashboardMapper.selectRecentAlerts(anyInt()))
                .thenReturn(List.of());
        when(dashboardMapper.selectTransactionsByHour(any(), any()))
                .thenReturn(List.of(hourTwo));
        when(dashboardMapper.selectAverageResolutionMinutes(any(), any()))
                .thenReturn(new BigDecimal("14.5"));
        when(dashboardMapper.selectResponseTimeTrend(any(), any()))
                .thenReturn(List.of());

        DashboardSnapshot snapshot = dashboardService.getSnapshot(
                DashboardUpdateType.FULL
        );

        assertThat(snapshot.getGeneratedAt()).isNotNull();
        assertThat(snapshot.getSummary().getOpenAlerts()).isEqualTo(12L);
        assertThat(snapshot.getSummary().getAcknowledgedAlerts()).isEqualTo(8L);
        assertThat(snapshot.getSummary().getTotalAlertsToday()).isEqualTo(30L);
        assertThat(snapshot.getSummary().getAlertsTodayChangePercent())
                .isEqualByComparingTo("50.0");
        assertThat(snapshot.getSummary().getAverageResolutionMinutes())
                .isEqualByComparingTo("14.5");
        assertThat(snapshot.getTransactionsOverTime()).hasSize(24);
        assertThat(snapshot.getTransactionsOverTime().get(2)
                .getTransactionCount()).isEqualTo(7L);
        assertThat(snapshot.getTransactionsOverTime().get(3)
                .getTransactionCount()).isZero();
        assertThat(snapshot.getAlertsBySeverity())
                .extracting(DashboardCategoryCount::getCategory)
                .containsExactly("HIGH", "MEDIUM", "LOW");
        assertThat(snapshot.getAlertStatusDistribution()).hasSize(5);
        assertThat(snapshot.getAlertResponseTimeTrend()).hasSize(7);
    }

    @Test
    void shouldReturnOnlyTransactionSectionForTransactionUpdate() {
        when(dashboardMapper.selectTransactionsByHour(any(), any()))
                .thenReturn(List.of());

        DashboardSnapshot snapshot = dashboardService.getSnapshot(
                DashboardUpdateType.TRANSACTIONS
        );

        assertThat(snapshot.getTransactionsOverTime()).hasSize(24);
        assertThat(snapshot.getSummary()).isNull();
        assertThat(snapshot.getAlertsBySeverity()).isNull();
        assertThat(snapshot.getAlertResponseTimeTrend()).isNull();
    }
}
