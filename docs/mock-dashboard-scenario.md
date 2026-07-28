# Dashboard模拟交易客户端

`scripts/mock-dashboard-scenario.mjs`用于演示以下完整链路：

```text
模拟客户端
  -> POST /api/transactions
  -> 交易以PENDING状态写入MySQL
  -> RabbitMQ发布交易列表
  -> 规则消费者执行当前启用规则
  -> 交易更新为NORMAL或ABNORMAL
  -> 命中规则时创建告警
  -> Dashboard WebSocket按5秒/15秒/60秒刷新页面
```

脚本使用Node.js原生`fetch`和`WebSocket`，不需要安装额外npm依赖。建议使用Node.js 22或更高版本。

## 启动准备

1. 启动MySQL和RabbitMQ。
2. 启动Spring Boot后端，默认端口为8080。
3. 启动前端并打开Dashboard页面。
4. 确认Monitoring Rules页面中需要演示的规则处于Enabled状态。

## 推荐演示命令

创建30笔交易，每2秒创建一笔：

```bash
node scripts/mock-dashboard-scenario.mjs
```

持续运行，直到按`Ctrl+C`：

```bash
node scripts/mock-dashboard-scenario.mjs --continuous --interval-ms 2000
```

更快完成一轮演示：

```bash
node scripts/mock-dashboard-scenario.mjs --count 20 --interval-ms 750
```

只查看将要生成的数据，不写入数据库：

```bash
node scripts/mock-dashboard-scenario.mjs --count 15 --dry-run
```

后端不是默认地址时：

```bash
node scripts/mock-dashboard-scenario.mjs \
  --base-url http://127.0.0.1:18080 \
  --count 20
```

## 场景构成

脚本启动时会调用`GET /api/rules?enabled=true`读取当前规则并动态计算交易参数。

| 场景 | 交易特点 | 预期业务结果 |
| --- | --- | --- |
| `BASELINE_WARMUP` | 低金额、固定账户和收款人 | 如果启用了New Payee，首次交易会产生告警 |
| `BASELINE_NORMAL` | 重复使用相同账户和收款人 | 通常更新为NORMAL |
| `HIGH_VALUE` | 金额高于当前Amount Threshold | 产生高额交易告警并更新为ABNORMAL |
| `NEW_PAYEE` | 每次使用不同收款人 | 产生New Payee告警并更新为ABNORMAL |
| `VELOCITY_*` | 连续创建`transactionCount + 1`笔同账户交易 | 最后一笔产生Velocity告警 |
| `DAILY_LIMIT` | 借记金额跨过当前Daily Limit | 产生Daily Limit告警 |

如果某种规则未启用，脚本仍会创建对应交易，但会提示该场景的最终状态取决于其他启用规则。

## 终端输出

`CREATE`表示API成功创建PENDING交易：

```text
[CREATE 3] HIGH_VALUE -> id=301 ref=TXN-... amount=12501 USD status=PENDING
```

`FLOW`表示RabbitMQ规则评估和数据库状态更新已经完成：

```text
[FLOW] TXN-... PENDING -> ABNORMAL | HIGH_VALUE | HIGH:High-value transaction
```

`DASHBOARD`表示脚本从`/ws/dashboard`收到页面数据更新：

```text
[DASHBOARD OPERATIONS] open=51 acknowledged=41 today=37 recent=5
[DASHBOARD TRANSACTIONS] todayCount=106
```

模拟数据使用运行时生成的`ACC-DEMO-*`账户编号，可以在Transactions和Alerts页面中搜索这些账户进行演示或后续清理。
