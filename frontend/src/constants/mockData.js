export const RULES = [
  { key: "RULE-001", name: "大额交易阈值", type: "AMOUNT_THRESHOLD", severity: "HIGH", active: true },
  { key: "RULE-002", name: "短时间高频交易", type: "VELOCITY", severity: "MEDIUM", active: true },
  { key: "RULE-003", name: "新收款方交易", type: "NEW_PAYEE", severity: "LOW", active: true },
  { key: "RULE-004", name: "跨境大额转账", type: "AMOUNT_THRESHOLD", severity: "HIGH", active: true },
];

export const RULE_NAME_BY_ID = Object.fromEntries(RULES.map((r) => [r.key, r.name]));

export const SEVERITY_OPTIONS = [
  { label: "HIGH", value: "HIGH" },
  { label: "MEDIUM", value: "MEDIUM" },
  { label: "LOW", value: "LOW" },
];
export const SEVERITY_COLOR = { HIGH: "red", MEDIUM: "gold", LOW: "blue" };

export const ALERT_STATUS_OPTIONS = [
  { label: "OPEN", value: "OPEN" },
  { label: "ACKNOWLEDGED", value: "ACKNOWLEDGED" },
  { label: "INVESTIGATING", value: "INVESTIGATING" },
  { label: "CLOSED", value: "CLOSED" },
  { label: "DISMISSED", value: "DISMISSED" },
];
export const ALERT_STATUS_COLOR = {
  OPEN: "red",
  ACKNOWLEDGED: "gold",
  INVESTIGATING: "blue",
  CLOSED: "green",
  DISMISSED: "default",
};

// today's date used for the "Alerts today" summary card
export const TODAY = "2026-07-27";

export const ALERTS = [
  {
    key: "ALT-5001",
    ruleId: "RULE-001",
    severity: "HIGH",
    status: "OPEN",
    createdAt: "2026-07-27 09:15:00",
    acknowledgedAt: null,
    acknowledgedBy: null,
    closedAt: null,
    closedBy: null,
    resolutionNotes: null,
    transactionIds: ["TXN-100234"],
  },
  {
    key: "ALT-5002",
    ruleId: "RULE-002",
    severity: "MEDIUM",
    status: "ACKNOWLEDGED",
    createdAt: "2026-07-27 08:40:00",
    acknowledgedAt: "2026-07-27 08:50:00",
    acknowledgedBy: "ivy.chen",
    closedAt: null,
    closedBy: null,
    resolutionNotes: null,
    transactionIds: ["TXN-100233", "TXN-100231"],
  },
  {
    key: "ALT-5003",
    ruleId: "RULE-003",
    severity: "LOW",
    status: "INVESTIGATING",
    createdAt: "2026-07-26 17:22:00",
    acknowledgedAt: "2026-07-26 17:30:00",
    acknowledgedBy: "marcus.li",
    closedAt: null,
    closedBy: null,
    resolutionNotes: null,
    transactionIds: ["TXN-100232"],
  },
  {
    key: "ALT-5004",
    ruleId: "RULE-001",
    severity: "HIGH",
    status: "CLOSED",
    createdAt: "2026-07-25 15:05:00",
    acknowledgedAt: "2026-07-25 15:20:00",
    acknowledgedBy: "ivy.chen",
    closedAt: "2026-07-25 17:05:00",
    closedBy: "ivy.chen",
    resolutionNotes: "客户确认为合法大额采购付款,已提供合同凭证。",
    transactionIds: ["TXN-100230"],
  },
  {
    key: "ALT-5005",
    ruleId: "RULE-004",
    severity: "HIGH",
    status: "DISMISSED",
    createdAt: "2026-07-24 11:12:00",
    acknowledgedAt: "2026-07-24 11:20:00",
    acknowledgedBy: "marcus.li",
    closedAt: "2026-07-24 11:40:00",
    closedBy: "marcus.li",
    resolutionNotes: "系统误判,交易对手方已在白名单中。",
    transactionIds: ["TXN-100229"],
  },
  {
    key: "ALT-5006",
    ruleId: "RULE-002",
    severity: "MEDIUM",
    status: "OPEN",
    createdAt: "2026-07-27 07:55:00",
    acknowledgedAt: null,
    acknowledgedBy: null,
    closedAt: null,
    closedBy: null,
    resolutionNotes: null,
    transactionIds: ["TXN-100227"],
  },
  {
    key: "ALT-5007",
    ruleId: "RULE-003",
    severity: "LOW",
    status: "DISMISSED",
    createdAt: "2026-07-23 09:30:00",
    acknowledgedAt: "2026-07-23 09:40:00",
    acknowledgedBy: "ivy.chen",
    closedAt: "2026-07-23 10:00:00",
    closedBy: "ivy.chen",
    resolutionNotes: "新收款方已通过人工核实,非风险交易。",
    transactionIds: ["TXN-100228"],
  },
];

export const TRANSACTION_STATUS_LABEL = {
  completed: "completed",
  pending: "pending",
  failed: "failed",
  reversed: "withdrawn",
};
export const TRANSACTION_STATUS_COLOR = {
  completed: "success",
  pending: "processing",
  failed: "error",
  reversed: "default",
};

export const TRANSACTION_TYPE_LABEL = { DEBIT: "DEBIT", CREDIT: "CREDIT" };
export const TRANSACTION_TYPE_COLOR = { DEBIT: "error", CREDIT: "success" };

export const TRANSACTIONS = [
  { key: "TXN-100234", accountId: "ACC-10021", counterpartyId: "CPTY-88823", type: "DEBIT", amount: 1250.0, currency: "USD", timestamp: "2026-07-26 09:31:12", description: "Wire transfer to vendor", status: "completed" },
  { key: "TXN-100233", accountId: "ACC-10021", counterpartyId: "CPTY-77210", type: "CREDIT", amount: 5400.5, currency: "USD", timestamp: "2026-07-26 09:20:04", description: "Client invoice payment", status: "completed" },
  { key: "TXN-100232", accountId: "ACC-10045", counterpartyId: "CPTY-45590", type: "DEBIT", amount: 320.75, currency: "EUR", timestamp: "2026-07-26 08:58:41", description: "Subscription renewal", status: "pending" },
  { key: "TXN-100231", accountId: "ACC-10021", counterpartyId: "CPTY-11002", type: "DEBIT", amount: 89.99, currency: "USD", timestamp: "2026-07-25 16:42:03", description: "Merchant POS purchase", status: "failed" },
  { key: "TXN-100230", accountId: "ACC-10078", counterpartyId: "CPTY-32871", type: "CREDIT", amount: 12000.0, currency: "GBP", timestamp: "2026-07-25 15:11:37", description: "Payroll deposit", status: "completed" },
  { key: "TXN-100229", accountId: "ACC-10045", counterpartyId: "CPTY-88823", type: "DEBIT", amount: 2100.0, currency: "EUR", timestamp: "2026-07-25 11:05:52", description: "Supplier settlement", status: "reversed" },
  { key: "TXN-100228", accountId: "ACC-10078", counterpartyId: "CPTY-99012", type: "CREDIT", amount: 640.2, currency: "GBP", timestamp: "2026-07-24 14:23:19", description: "Refund received", status: "completed" },
  { key: "TXN-100227", accountId: "ACC-10021", counterpartyId: "CPTY-45590", type: "DEBIT", amount: 415.0, currency: "USD", timestamp: "2026-07-24 10:02:47", description: "Utility bill payment", status: "pending" },
];

export const ACCOUNT_OPTIONS = [...new Set(TRANSACTIONS.map((t) => t.accountId))].map((id) => ({ label: id, value: id }));
export const CURRENCY_OPTIONS = [...new Set(TRANSACTIONS.map((t) => t.currency))].map((c) => ({ label: c, value: c }));
