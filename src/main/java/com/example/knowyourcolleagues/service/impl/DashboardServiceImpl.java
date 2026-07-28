package com.example.knowyourcolleagues.service.impl;

import com.example.knowyourcolleagues.dto.dashboard.DashboardCategoryCount;
import com.example.knowyourcolleagues.dto.dashboard.DashboardOperationalSummaryRow;
import com.example.knowyourcolleagues.dto.dashboard.DashboardResponseTimePoint;
import com.example.knowyourcolleagues.dto.dashboard.DashboardSnapshot;
import com.example.knowyourcolleagues.dto.dashboard.DashboardSummary;
import com.example.knowyourcolleagues.dto.dashboard.DashboardTransactionPoint;
import com.example.knowyourcolleagues.enums.AlertStatus;
import com.example.knowyourcolleagues.enums.DashboardUpdateType;
import com.example.knowyourcolleagues.mapper.DashboardMapper;
import com.example.knowyourcolleagues.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int RECENT_ALERT_LIMIT = 5;
    private static final int TREND_DAYS = 7;
    private static final BigDecimal TARGET_RESOLUTION_MINUTES =
            new BigDecimal("30.0");

    private final DashboardMapper dashboardMapper;
    private final Clock clock = Clock.systemUTC();

    @Override
    @Transactional(readOnly = true)
    public DashboardSnapshot getSnapshot(DashboardUpdateType updateType) {
        DashboardUpdateType effectiveType = updateType == null
                ? DashboardUpdateType.FULL
                : updateType;
        LocalDate today = LocalDate.now(clock);

        return switch (effectiveType) {
            case OPERATIONS -> operationsSnapshot(today);
            case TRANSACTIONS -> transactionSnapshot(today);
            case SLA -> slaSnapshot(today);
            case FULL -> fullSnapshot(today);
        };
    }

    private DashboardSnapshot fullSnapshot(LocalDate today) {
        DashboardSnapshot operations = operationsSnapshot(today);
        DashboardSnapshot transactions = transactionSnapshot(today);
        DashboardSnapshot sla = slaSnapshot(today);

        DashboardSummary summary = operations.getSummary();
        summary.setAverageResolutionMinutes(
                sla.getSummary().getAverageResolutionMinutes()
        );
        summary.setTargetResolutionMinutes(TARGET_RESOLUTION_MINUTES);

        operations.setTransactionsOverTime(
                transactions.getTransactionsOverTime()
        );
        operations.setAlertResponseTimeTrend(
                sla.getAlertResponseTimeTrend()
        );
        operations.setGeneratedAt(clock.instant());
        return operations;
    }

    private DashboardSnapshot operationsSnapshot(LocalDate today) {
        LocalDateTime todayStart = today.atStartOfDay();
        DashboardOperationalSummaryRow row =
                dashboardMapper.selectOperationalSummary(
                        today.minusDays(1).atStartOfDay(),
                        todayStart,
                        today.plusDays(1).atStartOfDay()
                );

        long openAlerts = valueOrZero(row == null ? null : row.getOpenAlerts());
        long acknowledgedAlerts = valueOrZero(
                row == null ? null : row.getAcknowledgedAlerts()
        );
        long alertsToday = valueOrZero(
                row == null ? null : row.getTotalAlertsToday()
        );
        long alertsYesterday = valueOrZero(
                row == null ? null : row.getTotalAlertsYesterday()
        );

        DashboardSummary summary = new DashboardSummary();
        summary.setOpenAlerts(openAlerts);
        summary.setAcknowledgedAlerts(acknowledgedAlerts);
        summary.setTotalAlertsToday(alertsToday);
        summary.setAlertsTodayChangePercent(
                percentageChange(alertsToday, alertsYesterday)
        );

        LocalDateTime windowStart = today.minusDays(TREND_DAYS - 1)
                .atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(1).atStartOfDay();

        DashboardSnapshot snapshot = baseSnapshot();
        snapshot.setSummary(summary);
        snapshot.setAlertsBySeverity(normalizeCategories(
                dashboardMapper.selectAlertsBySeverity(
                        windowStart,
                        windowEnd
                ),
                List.of("HIGH", "MEDIUM", "LOW")
        ));
        snapshot.setAlertStatusDistribution(normalizeCategories(
                dashboardMapper.selectAlertsByStatus(
                        windowStart,
                        windowEnd
                ),
                Arrays.stream(AlertStatus.values())
                        .map(Enum::name)
                        .toList()
        ));
        snapshot.setRecentAlerts(dashboardMapper.selectRecentAlerts(
                RECENT_ALERT_LIMIT
        ));
        return snapshot;
    }

    private DashboardSnapshot transactionSnapshot(LocalDate today) {
        List<DashboardTransactionPoint> databasePoints =
                dashboardMapper.selectTransactionsByHour(
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                );
        Map<Integer, DashboardTransactionPoint> byHour = safeList(
                databasePoints
        ).stream().collect(Collectors.toMap(
                DashboardTransactionPoint::getHourOfDay,
                Function.identity(),
                (first, ignored) -> first
        ));

        List<DashboardTransactionPoint> points = IntStream.range(0, 24)
                .mapToObj(hour -> new DashboardTransactionPoint(
                        hour,
                        String.format(Locale.ROOT, "%02d:00", hour),
                        byHour.containsKey(hour)
                                ? valueOrZero(byHour.get(hour)
                                .getTransactionCount())
                                : 0L
                ))
                .toList();

        DashboardSnapshot snapshot = baseSnapshot();
        snapshot.setTransactionsOverTime(points);
        return snapshot;
    }

    private DashboardSnapshot slaSnapshot(LocalDate today) {
        LocalDate windowStartDate = today.minusDays(TREND_DAYS - 1);
        LocalDateTime windowStart = windowStartDate.atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(1).atStartOfDay();

        DashboardSummary summary = new DashboardSummary();
        summary.setAverageResolutionMinutes(
                dashboardMapper.selectAverageResolutionMinutes(
                        windowStart,
                        windowEnd
                )
        );
        summary.setTargetResolutionMinutes(TARGET_RESOLUTION_MINUTES);

        Map<LocalDate, DashboardResponseTimePoint> byDate = safeList(
                dashboardMapper.selectResponseTimeTrend(
                        windowStart,
                        windowEnd
                )
        ).stream().collect(Collectors.toMap(
                DashboardResponseTimePoint::getDate,
                Function.identity(),
                (first, ignored) -> first
        ));

        List<DashboardResponseTimePoint> trend = IntStream.range(
                        0,
                        TREND_DAYS
                )
                .mapToObj(offset -> {
                    LocalDate date = windowStartDate.plusDays(offset);
                    DashboardResponseTimePoint existing = byDate.get(date);
                    return new DashboardResponseTimePoint(
                            date,
                            shortDayName(date.getDayOfWeek()),
                            existing == null
                                    ? null
                                    : existing.getAverageMinutes()
                    );
                })
                .toList();

        DashboardSnapshot snapshot = baseSnapshot();
        snapshot.setSummary(summary);
        snapshot.setAlertResponseTimeTrend(trend);
        return snapshot;
    }

    private DashboardSnapshot baseSnapshot() {
        DashboardSnapshot snapshot = new DashboardSnapshot();
        snapshot.setGeneratedAt(clock.instant());
        return snapshot;
    }

    private List<DashboardCategoryCount> normalizeCategories(
            List<DashboardCategoryCount> rawValues,
            List<String> categories
    ) {
        Map<String, Long> counts = safeList(rawValues).stream()
                .collect(Collectors.toMap(
                        DashboardCategoryCount::getCategory,
                        item -> valueOrZero(item.getCount()),
                        Long::sum
                ));
        return categories.stream()
                .map(category -> new DashboardCategoryCount(
                        category,
                        counts.getOrDefault(category, 0L)
                ))
                .toList();
    }

    private BigDecimal percentageChange(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? BigDecimal.ZERO : new BigDecimal("100.0");
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
    }

    private String shortDayName(DayOfWeek dayOfWeek) {
        return dayOfWeek.getDisplayName(
                TextStyle.SHORT,
                Locale.ENGLISH
        );
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
