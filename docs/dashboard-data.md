# Dashboard 数据说明

Dashboard 的首次完整数据和后续动态更新均来自数据库聚合结果，不再使用前端模拟数据。

## 更新机制

| 更新分区 | 默认频率 | 包含的数据块 | 选择原因 |
| --- | ---: | --- | --- |
| `FULL` | WebSocket 首次连接、手动 Refresh | 全部数据块 | 确保页面首次进入和人工刷新时获得一致快照 |
| `OPERATIONS` | 5秒 | Open Alerts、Acknowledged、Total Alerts Today、Alerts by Severity、Alert Status Distribution、Real-Time Triggered Alerts | 告警创建和处置属于操作员需要快速感知的数据 |
| `TRANSACTIONS` | 15秒 | Transactions Over Time | 交易会持续写入，但小时级趋势无需每秒聚合 |
| `SLA` | 60秒 | Avg Resolution SLA、Alert Response Time Trend | SLA属于低频变化的统计指标，降低聚合查询压力 |

默认频率可通过以下配置调整：

- `dashboard.websocket.operations-interval-ms`
- `dashboard.websocket.transactions-interval-ms`
- `dashboard.websocket.sla-interval-ms`

WebSocket地址为 `/ws/dashboard`。客户端连接成功后，后端立即发送`FULL`；客户端发送文本`REFRESH`时也会收到新的`FULL`快照。`GET /api/dashboard`提供相同的完整快照，便于Swagger联调和数据核对。

## 数据块口径与来源

| 数据块 | 使用数据 | 计算口径 | 数据来源 |
| --- | --- | --- | --- |
| Open Alerts | `status` | 当前`status = OPEN`的告警总数 | `alerts`表 |
| Acknowledged | `status` | 当前`status = ACKNOWLEDGED`的告警总数 | `alerts`表 |
| Total Alerts Today | `created_at` | 当前UTC自然日创建的告警数；同时与前一UTC自然日比较。前一日为0且今日大于0时，变化率显示100% | `alerts`表 |
| Avg Resolution SLA | `created_at`、`closed_at`、`dismissed_at`、`status` | 最近7个UTC自然日内进入`CLOSED`或`DISMISSED`的告警，从创建到终态的平均分钟数；目标值30分钟 | `alerts`表 |
| Transactions Over Time | `transaction_time`、`id` | 当前UTC自然日按小时统计交易记录数量`COUNT(*)`，固定返回00:00–23:00共24个点；不使用`amount` | `transactions`表 |
| Alerts by Severity | `severity`、`created_at` | 最近7个UTC自然日创建的告警，按`HIGH / MEDIUM / LOW`分组计数 | `alerts`表 |
| Alert Status Distribution | `status`、`created_at` | 最近7个UTC自然日创建的告警，按`OPEN / ACKNOWLEDGED / INVESTIGATING / CLOSED / DISMISSED`分组计数并计算占比 | `alerts`表 |
| Alert Response Time Trend | `created_at`、`acknowledged_at`、`investigating_at`、`closed_at`、`dismissed_at` | 最近7个UTC自然日按告警创建日期分组；首次人工响应时间使用首个非空生命周期时间，计算创建到首次响应的平均分钟数 | `alerts`表 |
| Real-Time Triggered Alerts | 告警信息和触发交易金额 | 按`created_at DESC, id DESC`读取最新5条告警，并显示其触发交易的金额和币种 | `alerts`关联`transactions`，连接条件为`alerts.trigger_transaction_id = transactions.id` |

## 后端数据路径

1. `DashboardMapper`执行数据库聚合查询。
2. `DashboardServiceImpl`补齐没有数据的小时、日期和枚举分组，形成稳定的数据结构。
3. `DashboardWebSocketHandler`在连接、手动刷新或定时任务触发时生成消息。
4. `DashboardUpdateScheduler`按三个频率层级广播部分快照。
5. 前端`DashBoardPage`把非空字段合并到当前快照，并只重绘收到更新的数据块。

为最新告警排序查询，`schema.sql`中的`alerts`表增加了`idx_alerts_created_at (created_at, id)`索引。已经创建的本地数据库需要单独执行等价的`ALTER TABLE`语句，或者在重新初始化数据库时应用最新schema。
