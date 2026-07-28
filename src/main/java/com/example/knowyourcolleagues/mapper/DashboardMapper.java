package com.example.knowyourcolleagues.mapper;

import com.example.knowyourcolleagues.dto.dashboard.DashboardCategoryCount;
import com.example.knowyourcolleagues.dto.dashboard.DashboardOperationalSummaryRow;
import com.example.knowyourcolleagues.dto.dashboard.DashboardRecentAlert;
import com.example.knowyourcolleagues.dto.dashboard.DashboardResponseTimePoint;
import com.example.knowyourcolleagues.dto.dashboard.DashboardTransactionPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 仪表盘聚合查询。所有查询均返回已经聚合的紧凑结果集。
 */
@Mapper
public interface DashboardMapper {

    @Select("""
            SELECT
                COALESCE(SUM(status = 'OPEN'), 0) AS openAlerts,
                COALESCE(SUM(status = 'ACKNOWLEDGED'), 0)
                    AS acknowledgedAlerts,
                COALESCE(SUM(created_at >= #{todayStart}
                    AND created_at < #{tomorrowStart}), 0)
                    AS totalAlertsToday,
                COALESCE(SUM(created_at >= #{yesterdayStart}
                    AND created_at < #{todayStart}), 0)
                    AS totalAlertsYesterday
            FROM alerts
            """)
    DashboardOperationalSummaryRow selectOperationalSummary(
            @Param("yesterdayStart") LocalDateTime yesterdayStart,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("tomorrowStart") LocalDateTime tomorrowStart
    );

    @Select("""
            SELECT ROUND(AVG(
                TIMESTAMPDIFF(
                    SECOND,
                    created_at,
                    CASE
                        WHEN status = 'CLOSED' THEN closed_at
                        WHEN status = 'DISMISSED' THEN dismissed_at
                    END
                ) / 60.0
            ), 1)
            FROM alerts
            WHERE status IN ('CLOSED', 'DISMISSED')
              AND COALESCE(closed_at, dismissed_at) >= #{windowStart}
              AND COALESCE(closed_at, dismissed_at) < #{windowEnd}
            """)
    BigDecimal selectAverageResolutionMinutes(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );

    @Select("""
            SELECT severity AS category, COUNT(*) AS count
            FROM alerts
            WHERE created_at >= #{windowStart}
              AND created_at < #{windowEnd}
            GROUP BY severity
            """)
    List<DashboardCategoryCount> selectAlertsBySeverity(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );

    @Select("""
            SELECT status AS category, COUNT(*) AS count
            FROM alerts
            WHERE created_at >= #{windowStart}
              AND created_at < #{windowEnd}
            GROUP BY status
            """)
    List<DashboardCategoryCount> selectAlertsByStatus(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );

    @Select("""
            SELECT HOUR(transaction_time) AS hourOfDay,
                   COUNT(*) AS transactionCount
            FROM transactions
            WHERE transaction_time >= #{todayStart}
              AND transaction_time < #{tomorrowStart}
            GROUP BY HOUR(transaction_time)
            ORDER BY hourOfDay
            """)
    List<DashboardTransactionPoint> selectTransactionsByHour(
            @Param("todayStart") LocalDateTime todayStart,
            @Param("tomorrowStart") LocalDateTime tomorrowStart
    );

    @Select("""
            SELECT DATE(created_at) AS date,
                   ROUND(AVG(
                       TIMESTAMPDIFF(
                           SECOND,
                           created_at,
                           COALESCE(
                               acknowledged_at,
                               investigating_at,
                               closed_at,
                               dismissed_at
                           )
                       ) / 60.0
                   ), 1) AS averageMinutes
            FROM alerts
            WHERE created_at >= #{windowStart}
              AND created_at < #{windowEnd}
              AND COALESCE(
                  acknowledged_at,
                  investigating_at,
                  closed_at,
                  dismissed_at
              ) IS NOT NULL
            GROUP BY DATE(created_at)
            ORDER BY date
            """)
    List<DashboardResponseTimePoint> selectResponseTimeTrend(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );

    @Select("""
            SELECT a.id,
                   a.rule_name AS ruleName,
                   a.severity,
                   a.account_id AS accountId,
                   t.amount AS triggerAmount,
                   t.currency,
                   a.status,
                   a.created_at AS createdAt,
                   a.title,
                   a.description
            FROM alerts a
            INNER JOIN transactions t
                ON t.id = a.trigger_transaction_id
            ORDER BY a.created_at DESC, a.id DESC
            LIMIT #{limit}
            """)
    List<DashboardRecentAlert> selectRecentAlerts(
            @Param("limit") int limit
    );
}
