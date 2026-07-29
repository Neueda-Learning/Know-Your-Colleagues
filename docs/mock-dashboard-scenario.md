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

脚本启动时会调用`GET /api/rules?enabled=true`读取当前规则并动态计算交易参数。每10笔交易构成一个完整账户场景，与两个SQL模拟脚本使用相同的数据关系：前9笔为高金额且使用不同Payee，第10笔复用第1个Payee并使用低金额。

| 场景位置 | 交易特点 | 预期业务结果 |
| --- | --- | --- |
| 第1-9笔 | 金额根据Amount Threshold和Daily Limit动态计算，每笔使用新的Payee | 命中已启用的Amount Threshold和New Payee规则 |
| `transactionCount + 1`位置 | 同一账户在规则时间窗口内连续交易 | 额外命中Velocity规则 |
| 首次累计金额越过Daily Limit的位置 | 同一账户、币种和UTC日期内的DEBIT累计金额首次超限 | 额外命中Daily Limit规则，并关联参与累计的交易 |
| 第10笔 | 低金额并复用第1笔的Payee | 默认规则下不命中规则，最终状态为NORMAL |

脚本会输出每轮使用的币种、触发金额、正常金额以及Velocity和Daily Limit的预期触发位置。如果实际状态或告警缺少预期规则，终端会输出`FLOW CHECK`提示。

## 终端输出

`CREATE`表示API成功创建PENDING交易：

```text
[CREATE 3] RULE_LINKED_3_OF_10 -> id=301 ref=TXN-... amount=12600 USD status=PENDING
```

`FLOW`表示RabbitMQ规则评估和数据库状态更新已经完成：

```text
[FLOW] TXN-... PENDING -> ABNORMAL | RULE_LINKED_3_OF_10 | HIGH:High-value transaction, LOW:New payee detected
```

`DASHBOARD`表示脚本从`/ws/dashboard`收到页面数据更新：

```text
[DASHBOARD OPERATIONS] open=51 acknowledged=41 today=37 recent=5
[DASHBOARD TRANSACTIONS] todayCount=106
```

模拟数据使用运行时生成的`ACC-DEMO-*`账户编号，可以在Transactions和Alerts页面中搜索这些账户进行演示或后续清理。
