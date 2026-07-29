#!/usr/bin/env node

const DEFAULT_OPTIONS = {
  baseUrl: "http://127.0.0.1:8080",
  count: 30,
  intervalMs: 2_000,
  statusTimeoutMs: 10_000,
  accountPrefix: "ACC-DEMO",
  watchDashboard: true,
  dryRun: false,
};

const HELP = `
Dashboard scenario simulator

Usage:
  node scripts/mock-dashboard-scenario.mjs [options]

Options:
  --base-url <url>             Backend URL (default: http://127.0.0.1:8080)
  --count <number>             Transactions to create (default: 30)
  --continuous                 Run until Ctrl+C
  --interval-ms <number>       Delay between transactions (default: 2000)
  --status-timeout-ms <number> Max wait for PENDING -> final status (default: 10000)
  --account-prefix <value>     Prefix for generated account IDs (default: ACC-DEMO)
  --no-dashboard-watch         Do not connect to /ws/dashboard
  --dry-run                    Print scenarios without creating transactions
  --help                       Show this help

Examples:
  node scripts/mock-dashboard-scenario.mjs --count 20 --interval-ms 1500
  node scripts/mock-dashboard-scenario.mjs --continuous --interval-ms 2000

Each ten-transaction cycle uses one account and mirrors the SQL demo dataset:
nine rule-linked high-value/new-payee payments followed by one low-value repeat.
`;

function parseArguments(argv) {
  const options = { ...DEFAULT_OPTIONS };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    const nextValue = () => {
      const value = argv[index + 1];
      if (!value || value.startsWith("--")) {
        throw new Error(`Missing value for ${argument}`);
      }
      index += 1;
      return value;
    };

    switch (argument) {
      case "--base-url":
        options.baseUrl = nextValue().replace(/\/$/, "");
        break;
      case "--count":
        options.count = parsePositiveInteger(nextValue(), argument);
        break;
      case "--continuous":
        options.count = Number.POSITIVE_INFINITY;
        break;
      case "--interval-ms":
        options.intervalMs = parsePositiveInteger(nextValue(), argument);
        break;
      case "--status-timeout-ms":
        options.statusTimeoutMs = parsePositiveInteger(nextValue(), argument);
        break;
      case "--account-prefix":
        options.accountPrefix = nextValue().toUpperCase();
        break;
      case "--no-dashboard-watch":
        options.watchDashboard = false;
        break;
      case "--dry-run":
        options.dryRun = true;
        break;
      case "--help":
        options.help = true;
        break;
      default:
        throw new Error(`Unknown option: ${argument}`);
    }
  }

  if (!/^[A-Z0-9-]{3,32}$/.test(options.accountPrefix)) {
    throw new Error("--account-prefix must contain 3-32 letters, numbers, or hyphens");
  }
  return options;
}

function parsePositiveInteger(value, optionName) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${optionName} must be a positive integer`);
  }
  return parsed;
}

function roundMoney(value) {
  return Math.max(0.01, Math.round(value * 100) / 100);
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  if (!response.ok) {
    const message = typeof body === "object" && body?.message
      ? body.message
      : typeof body === "string" && body
        ? body
        : `HTTP ${response.status}`;
    throw new Error(`${response.status} ${response.statusText}: ${message}`);
  }
  return body;
}

async function loadEnabledRules(baseUrl) {
  const response = await requestJson(
    `${baseUrl}/api/rules?enabled=true&page=0&size=100`
  );
  return response?.content ?? [];
}

function buildRuleContext(rules) {
  const findRule = (type) => rules.find((rule) => rule.type === type);
  const amountRule = findRule("AMOUNT_THRESHOLD");
  const velocityRule = findRule("VELOCITY");
  const newPayeeRule = findRule("NEW_PAYEE");
  const dailyLimitRule = findRule("DAILY_LIMIT");
  const scenarioCurrency = amountRule?.currency
    ?? dailyLimitRule?.currency
    ?? "USD";
  const amountThreshold = Number(amountRule?.thresholdAmount ?? 10_000);
  const dailyLimit = Number(dailyLimitRule?.dailyLimitAmount ?? 50_000);
  const ruleTriggerAmount = roundMoney(Math.max(
    amountThreshold + 100,
    dailyLimit / 4 + 100
  ));
  const normalAmount = roundMoney(Math.max(
    1,
    Math.min(100, amountThreshold / 10, dailyLimit / 100)
  ));
  const velocityCount = Number(velocityRule?.transactionCount ?? 5);
  const currencyMatches = (rule) => !rule?.currency
    || rule.currency.toUpperCase() === scenarioCurrency.toUpperCase();
  const dailyCrossingPosition = dailyLimitRule
    && currencyMatches(dailyLimitRule)
    ? Math.floor(dailyLimit / ruleTriggerAmount) + 1
    : null;

  return {
    amountRule,
    velocityRule,
    newPayeeRule,
    dailyLimitRule,
    scenarioCurrency,
    amountThreshold,
    dailyLimit,
    ruleTriggerAmount,
    normalAmount,
    velocityCount,
    velocityTriggerPosition: velocityCount + 1,
    dailyCrossingPosition,
    currencyMatches,
  };
}

function createScenarioPlanner(context, runId, accountPrefix) {
  const cycleLength = 10;

  return (index) => {
    const position = (index % cycleLength) + 1;
    const cycle = Math.floor(index / cycleLength) + 1;
    const accountId = `${accountPrefix}-${runId}-${String(cycle).padStart(3, "0")}`;
    const payeePrefix = `PAYEE-${runId}-${String(cycle).padStart(3, "0")}`;
    const isRuleTriggerPayment = position <= 9;
    const expectedRules = [];

    if (isRuleTriggerPayment
        && context.amountRule
        && context.currencyMatches(context.amountRule)
        && context.ruleTriggerAmount > context.amountThreshold) {
      expectedRules.push(context.amountRule.name);
    }
    if (isRuleTriggerPayment && context.newPayeeRule) {
      expectedRules.push(context.newPayeeRule.name);
    }
    if (position === context.velocityTriggerPosition && context.velocityRule) {
      expectedRules.push(context.velocityRule.name);
    }
    if (position === context.dailyCrossingPosition && context.dailyLimitRule) {
      expectedRules.push(context.dailyLimitRule.name);
    }

    const expected = expectedRules.length > 0
      ? `ABNORMAL: matches ${expectedRules.join(", ")}`
      : "NORMAL: known payee and no configured threshold crossing";
    const name = expectedRules.length > 0
      ? `RULE_LINKED_${position}_OF_${cycleLength}`
      : "KNOWN_PAYEE_NORMAL";

    return {
      name,
      expected,
      expectedRules,
      payload: {
        accountId,
        payeeId: position === 10
          ? `${payeePrefix}-01`
          : `${payeePrefix}-${String(position).padStart(2, "0")}`,
        amount: isRuleTriggerPayment
          ? context.ruleTriggerAmount
          : context.normalAmount,
        currency: context.scenarioCurrency,
        transactionType: "DEBIT",
        transactionTime: new Date().toISOString().slice(0, 19),
        description: isRuleTriggerPayment
          ? `Dashboard rule-linked payment ${position} of 9, cycle ${cycle}`
          : `Dashboard known-payee low-value payment, cycle ${cycle}`,
      },
    };
  };
}

async function createTransaction(baseUrl, scenario) {
  return requestJson(`${baseUrl}/api/transactions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(scenario.payload),
  });
}

async function waitForFinalStatus(baseUrl, transactionId, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const transaction = await requestJson(
      `${baseUrl}/api/transactions/${transactionId}`
    );
    if (transaction.status !== "PENDING") return transaction;
    await sleep(250);
  }
  throw new Error(`Transaction ${transactionId} remained PENDING for ${timeoutMs}ms`);
}

async function loadTransactionAlerts(baseUrl, transaction, startedAt) {
  const query = new URLSearchParams({
    accountId: transaction.accountId,
    createdAtStart: startedAt,
    page: "0",
    size: "100",
  });
  const response = await requestJson(`${baseUrl}/api/alerts?${query}`);
  return (response?.content ?? []).filter(
    (alert) => alert.triggerTransactionId === transaction.id
  );
}

async function trackBusinessFlow(baseUrl, created, scenario, startedAt, timeoutMs) {
  try {
    const finalTransaction = await waitForFinalStatus(
      baseUrl,
      created.id,
      timeoutMs
    );
    const alerts = finalTransaction.status === "ABNORMAL"
      ? await loadTransactionAlerts(baseUrl, finalTransaction, startedAt)
      : [];
    const alertSummary = alerts.length === 0
      ? "no alerts"
      : alerts.map((alert) => `${alert.severity}:${alert.ruleName}`).join(", ");
    const actualRuleNames = new Set(alerts.map((alert) => alert.ruleName));
    const missingExpectedRules = scenario.expectedRules.filter(
      (ruleName) => !actualRuleNames.has(ruleName)
    );
    const expectedStatus = scenario.expectedRules.length > 0
      ? "ABNORMAL"
      : "NORMAL";
    console.log(
      `[FLOW] ${created.transactionRef} PENDING -> ${finalTransaction.status}`
      + ` | ${scenario.name} | ${alertSummary}`
    );
    if (finalTransaction.status !== expectedStatus) {
      console.warn(
        `[FLOW CHECK] ${created.transactionRef} expected status `
        + `${expectedStatus}, received ${finalTransaction.status}`
      );
    }
    if (missingExpectedRules.length > 0) {
      console.warn(
        `[FLOW CHECK] ${created.transactionRef} missing expected alert(s): `
        + missingExpectedRules.join(", ")
      );
    }
    return finalTransaction;
  } catch (error) {
    console.error(`[FLOW ERROR] transaction=${created.id} ${error.message}`);
    throw error;
  }
}

function dashboardWebSocketUrl(baseUrl) {
  const url = new URL(baseUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws/dashboard";
  url.search = "";
  return url.toString();
}

function startDashboardWatcher(baseUrl) {
  if (typeof WebSocket === "undefined") {
    console.warn("[DASHBOARD] Native WebSocket is unavailable; use Node.js 22 or newer");
    return { close() {} };
  }

  const socket = new WebSocket(dashboardWebSocketUrl(baseUrl));
  socket.addEventListener("open", () => {
    console.log("[DASHBOARD] WebSocket connected");
  });
  socket.addEventListener("message", (event) => {
    try {
      const message = JSON.parse(event.data);
      const data = message.data ?? {};
      if (message.type === "FULL" || message.type === "OPERATIONS") {
        const summary = data.summary ?? {};
        console.log(
          `[DASHBOARD ${message.type}] open=${summary.openAlerts ?? "-"}`
          + ` acknowledged=${summary.acknowledgedAlerts ?? "-"}`
          + ` today=${summary.totalAlertsToday ?? "-"}`
          + ` recent=${data.recentAlerts?.length ?? "-"}`
        );
      } else if (message.type === "TRANSACTIONS") {
        const total = (data.transactionsOverTime ?? []).reduce(
          (sum, point) => sum + Number(point.transactionCount ?? 0),
          0
        );
        console.log(`[DASHBOARD TRANSACTIONS] todayCount=${total}`);
      } else if (message.type === "SLA") {
        console.log(
          `[DASHBOARD SLA] avgResolutionMinutes=`
          + `${data.summary?.averageResolutionMinutes ?? "n/a"}`
        );
      }
    } catch (error) {
      console.warn(`[DASHBOARD] Ignored malformed message: ${error.message}`);
    }
  });
  socket.addEventListener("error", () => {
    console.warn("[DASHBOARD] WebSocket connection error");
  });
  socket.addEventListener("close", () => {
    console.log("[DASHBOARD] WebSocket disconnected");
  });
  return {
    close() {
      if (socket.readyState === WebSocket.OPEN) {
        socket.close(1000, "Scenario completed");
      }
    },
  };
}

function printRuleSummary(rules, context) {
  console.log(`[RULES] ${rules.length} enabled rule(s)`);
  for (const rule of rules) {
    const parameter = rule.type === "AMOUNT_THRESHOLD"
      ? `threshold=${rule.thresholdAmount} ${rule.currency ?? "ANY"}`
      : rule.type === "VELOCITY"
        ? `count=${rule.transactionCount + 1} within ${rule.timeWindowMinutes}m`
        : rule.type === "DAILY_LIMIT"
          ? `limit=${rule.dailyLimitAmount} ${rule.currency ?? "ANY"}`
          : "first account/payee combination";
    console.log(`  - ${rule.type}: ${rule.name} (${parameter})`);
  }
  console.log(
    `[PLAN] cycle=10 currency=${context.scenarioCurrency}`
    + ` triggerAmount=${context.ruleTriggerAmount}`
    + ` normalAmount=${context.normalAmount}`
    + ` velocityPosition=${context.velocityTriggerPosition}`
    + ` dailyPosition=${context.dailyCrossingPosition ?? "none"}`
  );
  if (!context.amountRule) console.warn("[RULES] No enabled AMOUNT_THRESHOLD rule; amount alerts are not expected");
  if (!context.velocityRule) console.warn("[RULES] No enabled VELOCITY rule; velocity alerts are not expected");
  if (!context.newPayeeRule) console.warn("[RULES] No enabled NEW_PAYEE rule; new-payee alerts are not expected");
  if (!context.dailyLimitRule) console.warn("[RULES] No enabled DAILY_LIMIT rule; daily-limit alerts are not expected");
  if (context.velocityTriggerPosition > 10) {
    console.warn("[RULES] VELOCITY requires more than one ten-transaction cycle and will not match this account plan");
  }
  if (context.dailyLimitRule && context.dailyCrossingPosition > 9) {
    console.warn("[RULES] DAILY_LIMIT is not crossed by the nine rule-linked payments");
  }
}

async function main() {
  const options = parseArguments(process.argv.slice(2));
  if (options.help) {
    console.log(HELP.trim());
    return;
  }

  const runId = new Date().toISOString().replace(/\D/g, "").slice(8, 14);
  const startedAt = new Date().toISOString();
  let stopped = false;
  process.on("SIGINT", () => {
    stopped = true;
    console.log("\n[SCENARIO] Stop requested; waiting for in-flight transactions...");
  });

  console.log(`[SCENARIO] backend=${options.baseUrl}`);
  console.log(
    `[SCENARIO] count=${Number.isFinite(options.count) ? options.count : "continuous"}`
    + ` interval=${options.intervalMs}ms runId=${runId}`
  );

  const rules = await loadEnabledRules(options.baseUrl);
  const context = buildRuleContext(rules);
  printRuleSummary(rules, context);
  const planScenario = createScenarioPlanner(
    context,
    runId,
    options.accountPrefix
  );
  const dashboardWatcher = options.watchDashboard && !options.dryRun
    ? startDashboardWatcher(options.baseUrl)
    : { close() {} };
  const inFlight = new Set();
  let createdCount = 0;
  let failedCount = 0;

  try {
    for (let index = 0; index < options.count && !stopped; index += 1) {
      const scenario = planScenario(index);
      if (options.dryRun) {
        console.log(
          `[DRY RUN ${index + 1}] ${scenario.name}`
          + ` account=${scenario.payload.accountId}`
          + ` payee=${scenario.payload.payeeId}`
          + ` amount=${scenario.payload.amount} ${scenario.payload.currency}`
          + ` | ${scenario.expected}`
        );
      } else {
        try {
          const created = await createTransaction(options.baseUrl, scenario);
          createdCount += 1;
          console.log(
            `[CREATE ${createdCount}] ${scenario.name}`
            + ` -> id=${created.id} ref=${created.transactionRef}`
            + ` amount=${created.amount} ${created.currency} status=${created.status}`
          );
          const tracking = trackBusinessFlow(
            options.baseUrl,
            created,
            scenario,
            startedAt,
            options.statusTimeoutMs
          ).catch(() => {
            failedCount += 1;
          }).finally(() => inFlight.delete(tracking));
          inFlight.add(tracking);
        } catch (error) {
          failedCount += 1;
          console.error(`[CREATE ERROR] ${scenario.name}: ${error.message}`);
        }
      }

      if (!options.dryRun && index + 1 < options.count && !stopped) {
        await sleep(options.intervalMs);
      }
    }

    await Promise.allSettled([...inFlight]);
  } finally {
    dashboardWatcher.close();
  }

  console.log(
    `[SCENARIO COMPLETE] created=${createdCount}`
    + ` failed=${failedCount}`
    + ` stopped=${stopped}`
  );
  if (failedCount > 0) process.exitCode = 1;
}

main().catch((error) => {
  console.error(`[FATAL] ${error.message}`);
  process.exitCode = 1;
});
