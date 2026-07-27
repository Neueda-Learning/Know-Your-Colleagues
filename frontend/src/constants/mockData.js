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
