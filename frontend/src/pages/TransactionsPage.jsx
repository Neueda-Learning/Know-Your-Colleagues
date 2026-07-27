import { useMemo, useState } from "react";
import { Table, Tag, Input } from "antd";
import { Search } from "lucide-react";
import { COLORS } from "../constants/theme";
import {
  TRANSACTIONS,
  TRANSACTION_STATUS_LABEL,
  TRANSACTION_STATUS_COLOR,
  TRANSACTION_TYPE_LABEL,
  TRANSACTION_TYPE_COLOR,
  ACCOUNT_OPTIONS,
  CURRENCY_OPTIONS,
} from "../constants/mockData";

const monoCell = (content, extraStyle = {}) => (
  <span style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: 13, color: COLORS.slate, ...extraStyle }}>
    {content}
  </span>
);

export default function TransactionsPage() {
  const [keyword, setKeyword] = useState("");

  const filteredData = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    if (!q) return TRANSACTIONS;
    return TRANSACTIONS.filter(
      (row) =>
        row.key.toLowerCase().includes(q) ||
        row.accountId.toLowerCase().includes(q) ||
        row.counterpartyId.toLowerCase().includes(q) ||
        row.description.toLowerCase().includes(q)
    );
  }, [keyword]);

  const columns = [
    {
      title: "Transaction ID",
      dataIndex: "key",
      key: "key",
      fixed: "left",
      render: (v) => monoCell(v, { color: COLORS.ink, fontWeight: 500 }),
    },
    {
      title: "Account ID",
      dataIndex: "accountId",
      key: "accountId",
      filters: ACCOUNT_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
      onFilter: (value, row) => row.accountId === value,
      render: (v) => monoCell(v),
    },
    {
      title: "Counterparty ID",
      dataIndex: "counterpartyId",
      key: "counterpartyId",
      render: (v) => monoCell(v),
    },
    {
      title: "Type",
      dataIndex: "type",
      key: "type",
      filters: [
        { text: "DEBIT", value: "DEBIT" },
        { text: "CREDIT", value: "CREDIT" },
      ],
      onFilter: (value, row) => row.type === value,
      render: (type) => <Tag color={TRANSACTION_TYPE_COLOR[type]}>{TRANSACTION_TYPE_LABEL[type]}</Tag>,
    },
    {
      title: "Amount",
      dataIndex: "amount",
      key: "amount",
      align: "right",
      sorter: (a, b) => a.amount - b.amount,
      render: (v, row) => (
        <span
          style={{
            fontFamily: "'IBM Plex Mono', monospace",
            fontSize: 14,
            fontWeight: 500,
            color: row.type === "DEBIT" ? COLORS.red : COLORS.green,
          }}
        >
          {row.type === "DEBIT" ? "-" : "+"}
          {v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </span>
      ),
    },
    {
      title: "Currency",
      dataIndex: "currency",
      key: "currency",
      filters: CURRENCY_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
      onFilter: (value, row) => row.currency === value,
      render: (v) => monoCell(v, { color: COLORS.ink }),
    },
    {
      title: "Timestamp",
      dataIndex: "timestamp",
      key: "timestamp",
      sorter: (a, b) => a.timestamp.localeCompare(b.timestamp),
      defaultSortOrder: "descend",
      render: (v) => monoCell(v),
    },
    {
      title: "Description",
      dataIndex: "description",
      key: "description",
      render: (v) => <span style={{ fontSize: 13, color: COLORS.slate }}>{v}</span>,
    },
    {
      title: "Status",
      dataIndex: "status",
      key: "status",
      filters: Object.entries(TRANSACTION_STATUS_LABEL).map(([value, text]) => ({ text, value })),
      onFilter: (value, row) => row.status === value,
      render: (status) => <Tag color={TRANSACTION_STATUS_COLOR[status]}>{TRANSACTION_STATUS_LABEL[status]}</Tag>,
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <Input
        allowClear
        placeholder="search Transaction ID / Account ID / Counterparty ID / Description"
        prefix={<Search size={15} color={COLORS.slate} />}
        style={{ maxWidth: 380 }}
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />

      <div style={{ background: COLORS.card, border: `1px solid ${COLORS.border}`, borderRadius: 12, overflow: "hidden" }}>
        <Table
          columns={columns}
          dataSource={filteredData}
          size="middle"
          scroll={{ x: 1100 }}
          pagination={{ pageSize: 6, showTotal: (total) => `${total} transactions` }}
        />
      </div>
    </div>
  );
}
