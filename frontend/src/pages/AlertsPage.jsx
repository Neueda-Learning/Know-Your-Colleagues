import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Table,
  Tag,
  DatePicker,
  Drawer,
  Descriptions,
  Button,
  Space,
  Form,
  Input,
  Select,
  Timeline,
  message,
  Spin,
} from "antd";
import { Search, Calendar } from "lucide-react";
import { COLORS } from "../constants/theme";
import {
  SEVERITY_OPTIONS,
  SEVERITY_COLOR,
  ALERT_STATUS_OPTIONS,
  ALERT_STATUS_COLOR,
} from "../constants/mockData";
import AlertSummaryCard from "../components/AlertSummaryCard";
import { fetchAlerts, fetchAlert, updateAlertStatus } from "../api/alerts";
import { fetchTransaction } from "../api/transactions";

const { RangePicker } = DatePicker;

const PAGE_SIZE = 20;

const fieldLabelStyle = {
  display: "block",
  fontSize: 12,
  fontWeight: 600,
  color: COLORS.ink,
  marginBottom: 6,
};

const filterInputStyle = {
  width: "100%",
};

function toDate(value) {
  if (!value) return null;
  if (value instanceof Date) return value;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

function formatDateTime(value) {
  const d = toDate(value);
  if (!d) return "—";
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function formatDuration(minutes) {
  if (minutes == null || Number.isNaN(minutes)) return "—";
  if (minutes < 60) return `${Math.round(minutes)} mins`;
  const h = Math.floor(minutes / 60);
  const m = Math.round(minutes % 60);
  return `${h} hours ${m} mins`;
}

function isSameLocalDay(value, day = new Date()) {
  const d = toDate(value);
  if (!d) return false;
  return (
    d.getFullYear() === day.getFullYear() &&
    d.getMonth() === day.getMonth() &&
    d.getDate() === day.getDate()
  );
}

function mapAlertRow(alert) {
  return {
    key: alert.id,
    id: alert.id,
    ruleId: alert.ruleId,
    ruleName: alert.ruleName,
    triggerTransactionId: alert.triggerTransactionId,
    accountId: alert.accountId,
    severity: alert.severity,
    status: alert.status,
    title: alert.title,
    createdAt: alert.createdAt,
    updatedAt: alert.updatedAt,
  };
}

// Align with backend: OPEN → ACKNOWLEDGED/DISMISSED;
// ACKNOWLEDGED → INVESTIGATING/DISMISSED; INVESTIGATING → CLOSED/DISMISSED.
const NEXT_ACTIONS = {
  OPEN: [
    { to: "ACKNOWLEDGED", label: "Acknowledge", type: "primary" },
    { to: "DISMISSED", label: "Dismiss as False Positive", danger: true },
  ],
  ACKNOWLEDGED: [
    { to: "INVESTIGATING", label: "Mark as Investigating", type: "primary" },
    { to: "DISMISSED", label: "Dismiss as False Positive", danger: true },
  ],
  INVESTIGATING: [
    { to: "CLOSED", label: "Close Alert", type: "primary" },
    { to: "DISMISSED", label: "Dismiss as False Positive", danger: true },
  ],
  CLOSED: [],
  DISMISSED: [],
};

export default function AlertsPage() {
  const [filters, setFilters] = useState({
    severity: undefined,
    status: undefined,
    accountId: "",
    dateRange: null,
  });
  const [applied, setApplied] = useState({
    severity: undefined,
    status: undefined,
    accountId: "",
    dateRange: null,
  });

  const [alerts, setAlerts] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [loading, setLoading] = useState(false);

  const [openCount, setOpenCount] = useState(0);
  const [ackCount, setAckCount] = useState(0);
  const [todayCount, setTodayCount] = useState(0);
  const [avgResolutionMinutes, setAvgResolutionMinutes] = useState(null);

  const [selected, setSelected] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [relatedTransactions, setRelatedTransactions] = useState([]);
  const [pendingAction, setPendingAction] = useState(null);
  const [updating, setUpdating] = useState(false);
  const [form] = Form.useForm();

  const handleSearch = () => {
    setApplied({ ...filters });
    setPage(0);
  };

  const loadStats = useCallback(async () => {
    try {
      const [openRes, ackRes, sampleRes] = await Promise.all([
        fetchAlerts({ status: "OPEN", page: 0, size: 1 }),
        fetchAlerts({ status: "ACKNOWLEDGED", page: 0, size: 1 }),
        fetchAlerts({ page: 0, size: 100 }),
      ]);
      setOpenCount(openRes.totalElements ?? 0);
      setAckCount(ackRes.totalElements ?? 0);

      const sample = sampleRes.content ?? [];
      setTodayCount(sample.filter((a) => isSameLocalDay(a.createdAt)).length);

      const resolved = sample.filter((a) => a.status === "CLOSED" || a.status === "DISMISSED");
      if (resolved.length === 0) {
        setAvgResolutionMinutes(null);
      } else {
        const totalMins = resolved.reduce((sum, a) => {
          const start = toDate(a.createdAt);
          const end = toDate(a.updatedAt);
          if (!start || !end) return sum;
          return sum + (end - start) / 60000;
        }, 0);
        setAvgResolutionMinutes(totalMins / resolved.length);
      }
    } catch {
      // summary cards are best-effort; list errors are handled separately
    }
  }, []);

  const loadAlerts = useCallback(async () => {
    setLoading(true);
    try {
      const params = {
        page,
        size: pageSize,
      };
      if (applied.severity) params.severity = applied.severity;
      if (applied.status) params.status = applied.status;
      if (applied.accountId.trim()) params.accountId = applied.accountId.trim();
      // Backend only supports createdAtStart (from); "to" is filtered on the current page.
      if (applied.dateRange?.[0]) {
        params.createdAtStart = applied.dateRange[0].startOf("day").toISOString();
      }

      const result = await fetchAlerts(params);
      setAlerts((result.content ?? []).map(mapAlertRow));
      setTotal(result.totalElements ?? 0);
    } catch (err) {
      message.error(err.message || "Failed to load alerts");
      setAlerts([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, applied]);

  useEffect(() => {
    loadAlerts();
  }, [loadAlerts]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  const displayAlerts = useMemo(() => {
    const end = applied.dateRange?.[1];
    if (!end) return alerts;
    const endTime = end.endOf("day").toDate();
    return alerts.filter((a) => {
      const created = toDate(a.createdAt);
      return created && created <= endTime;
    });
  }, [alerts, applied.dateRange]);

  const columns = [
    {
      title: "Alert ID",
      dataIndex: "id",
      key: "id",
      render: (v) => (
        <span style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: 13, color: COLORS.ink, fontWeight: 500 }}>
          {v}
        </span>
      ),
    },
    {
      title: "Severity",
      dataIndex: "severity",
      key: "severity",
      render: (s) => <Tag color={SEVERITY_COLOR[s]}>{s}</Tag>,
    },
    {
      title: "Rule Name",
      dataIndex: "ruleName",
      key: "ruleName",
      render: (name, row) => (
        <span style={{ fontSize: 13, color: COLORS.ink }}>{name || row.ruleId || "—"}</span>
      ),
    },
    {
      title: "Account ID",
      dataIndex: "accountId",
      key: "accountId",
      render: (v) => (
        <span style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: 13, color: COLORS.slate }}>
          {v || "—"}
        </span>
      ),
    },
    {
      title: "Status",
      dataIndex: "status",
      key: "status",
      render: (s) => <Tag color={ALERT_STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: "Created Time",
      dataIndex: "createdAt",
      key: "createdAt",
      render: (v) => (
        <span style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: 13, color: COLORS.slate }}>
          {formatDateTime(v)}
        </span>
      ),
    },
  ];

  const openDetail = async (row) => {
    setSelected(row);
    setPendingAction(null);
    setRelatedTransactions([]);
    form.resetFields();
    setDetailLoading(true);
    try {
      const detail = await fetchAlert(row.id);
      setSelected(detail);

      const ids = [
        ...(detail.relatedTransactionIds ?? []),
        detail.triggerTransactionId,
      ].filter((id, idx, arr) => id != null && arr.indexOf(id) === idx);

      if (ids.length > 0) {
        const results = await Promise.allSettled(ids.map((id) => fetchTransaction(id)));
        setRelatedTransactions(
          results
            .filter((r) => r.status === "fulfilled")
            .map((r) => r.value)
            .map((tx) => ({
              key: tx.id,
              id: tx.id,
              transactionRef: tx.transactionRef,
              payeeId: tx.payeeId,
              amount: Number(tx.amount),
              currency: tx.currency,
              transactionType: tx.transactionType,
              transactionTime: tx.transactionTime,
            }))
        );
      }
    } catch (err) {
      message.error(err.message || "Failed to load alert details");
    } finally {
      setDetailLoading(false);
    }
  };

  const applyTransition = async (to, notes) => {
    if (!selected?.id) return;
    setUpdating(true);
    try {
      const detail = await updateAlertStatus(selected.id, {
        targetStatus: to,
        notes: notes || undefined,
      });
      message.success(`Alert has been updated to ${to}`);
      setSelected(detail);
      setPendingAction(null);
      form.resetFields();
      await Promise.all([loadAlerts(), loadStats()]);
    } catch (err) {
      message.error(err.message || "Failed to update alert status");
    } finally {
      setUpdating(false);
    }
  };

  const handleActionClick = (action) => {
    if (action.to === "CLOSED" || action.to === "DISMISSED") {
      setPendingAction(action.to);
    } else {
      applyTransition(action.to);
    }
  };

  const historyItems = (selected?.history ?? []).map((h) => ({
    color: ALERT_STATUS_COLOR[h.toStatus] || "gray",
    children: (
      <div>
        <div>
          {h.fromStatus ? `${h.fromStatus} → ${h.toStatus}` : h.toStatus}
          {" · "}
          {formatDateTime(h.changedAt)}
        </div>
        {h.notes && (
          <div style={{ fontSize: 12, color: COLORS.slate, marginTop: 4 }}>{h.notes}</div>
        )}
      </div>
    ),
  }));

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
        <AlertSummaryCard label="Open Alerts" value={openCount} color={COLORS.red} bg={COLORS.redSoft} />
        <AlertSummaryCard label="Acknowledged" value={ackCount} color={COLORS.amber} bg={COLORS.amberSoft} />
        <AlertSummaryCard label="Alerts Today" value={todayCount} color={COLORS.accent} bg={COLORS.accentSoft} />
        <AlertSummaryCard
          label="Avg Resolution Time"
          value={formatDuration(avgResolutionMinutes)}
          color={COLORS.green}
          bg={COLORS.greenSoft}
        />
      </div>

      <div
        style={{
          background: COLORS.card,
          border: `1px solid ${COLORS.border}`,
          borderRadius: 12,
          padding: "16px 20px",
        }}
      >
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))",
            gap: 12,
            alignItems: "end",
          }}
        >
          <div>
            <label style={fieldLabelStyle}>Severity</label>
            <Select
              allowClear
              placeholder="Select severity"
              style={filterInputStyle}
              value={filters.severity}
              options={SEVERITY_OPTIONS}
              onChange={(severity) => setFilters((prev) => ({ ...prev, severity }))}
            />
          </div>

          <div>
            <label style={fieldLabelStyle}>Status</label>
            <Select
              allowClear
              placeholder="Select status"
              style={filterInputStyle}
              value={filters.status}
              options={ALERT_STATUS_OPTIONS}
              onChange={(status) => setFilters((prev) => ({ ...prev, status }))}
            />
          </div>

          <div>
            <label style={fieldLabelStyle}>Account ID</label>
            <Input
              allowClear
              placeholder="Enter account ID"
              style={filterInputStyle}
              value={filters.accountId}
              onChange={(e) => setFilters((prev) => ({ ...prev, accountId: e.target.value }))}
              onPressEnter={handleSearch}
            />
          </div>

          <div>
            <label style={fieldLabelStyle}>Created Time</label>
            <RangePicker
              style={{ width: "100%" }}
              placeholder={["From", "To"]}
              value={filters.dateRange}
              onChange={(dateRange) => setFilters((prev) => ({ ...prev, dateRange }))}
              prefix={<Calendar size={14} color={COLORS.slate} />}
              allowClear
            />
          </div>

          <div>
            <label style={fieldLabelStyle}>&nbsp;</label>
            <Button
              type="primary"
              icon={<Search size={15} />}
              onClick={handleSearch}
              loading={loading}
              aria-label="Search"
              style={{ background: COLORS.accent }}
            />
          </div>
        </div>
      </div>

      <div style={{ background: COLORS.card, border: `1px solid ${COLORS.border}`, borderRadius: 12, overflow: "hidden" }}>
        <Table
          columns={columns}
          dataSource={displayAlerts}
          loading={loading}
          size="middle"
          onRow={(row) => ({ onClick: () => openDetail(row), style: { cursor: "pointer" } })}
          onChange={(pagination) => {
            const nextPage = (pagination.current ?? 1) - 1;
            const nextSize = pagination.pageSize ?? pageSize;
            if (nextSize !== pageSize) {
              setPageSize(nextSize);
              setPage(0);
            } else {
              setPage(nextPage);
            }
          }}
          pagination={{
            current: page + 1,
            pageSize,
            total: applied.dateRange?.[1] ? displayAlerts.length : total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (t) => `${t} Alerts`,
          }}
        />
      </div>

      <Drawer
        title={selected ? `Alert #${selected.id}` : "Alert Details"}
        open={!!selected}
        onClose={() => {
          setSelected(null);
          setPendingAction(null);
          setRelatedTransactions([]);
        }}
        width={520}
      >
        <Spin spinning={detailLoading || updating}>
          {selected && (
            <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
              <Descriptions column={1} size="small" bordered>
                <Descriptions.Item label="Title">{selected.title || "—"}</Descriptions.Item>
                <Descriptions.Item label="Rule Name">
                  {selected.ruleName || selected.ruleId || "—"}
                </Descriptions.Item>
                <Descriptions.Item label="Account ID">{selected.accountId || "—"}</Descriptions.Item>
                <Descriptions.Item label="Severity">
                  <Tag color={SEVERITY_COLOR[selected.severity]}>{selected.severity}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Status">
                  <Tag color={ALERT_STATUS_COLOR[selected.status]}>{selected.status}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Created">{formatDateTime(selected.createdAt)}</Descriptions.Item>
                {selected.description && (
                  <Descriptions.Item label="Description">{selected.description}</Descriptions.Item>
                )}
                {selected.resolutionNotes && (
                  <Descriptions.Item label="Resolution Notes">{selected.resolutionNotes}</Descriptions.Item>
                )}
              </Descriptions>

              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: COLORS.ink, marginBottom: 10 }}>
                  Alert Status History
                </div>
                {historyItems.length > 0 ? (
                  <Timeline items={historyItems} />
                ) : (
                  <div style={{ fontSize: 13, color: COLORS.slate }}>No history yet</div>
                )}
              </div>

              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: COLORS.ink, marginBottom: 8 }}>
                  Related Transactions
                </div>
                <Table
                  size="small"
                  pagination={false}
                  locale={{ emptyText: "No related transactions" }}
                  dataSource={relatedTransactions}
                  columns={[
                    {
                      title: "Transaction ID",
                      dataIndex: "transactionRef",
                      render: (v, row) => v || row.id,
                    },
                    { title: "Payee", dataIndex: "payeeId" },
                    {
                      title: "Amount",
                      dataIndex: "amount",
                      align: "right",
                      render: (v, row) =>
                        `${row.transactionType === "DEBIT" ? "-" : "+"}${Number(v).toFixed(2)} ${row.currency}`,
                    },
                    {
                      title: "Time",
                      dataIndex: "transactionTime",
                      render: (v) => formatDateTime(v),
                    },
                  ]}
                />
              </div>

              {(NEXT_ACTIONS[selected.status] ?? []).length > 0 && !pendingAction && (
                <Space wrap>
                  {NEXT_ACTIONS[selected.status].map((action) => (
                    <Button
                      key={action.to}
                      type={action.type}
                      danger={action.danger}
                      onClick={() => handleActionClick(action)}
                    >
                      {action.label}
                    </Button>
                  ))}
                </Space>
              )}

              {pendingAction && (
                <Form
                  form={form}
                  layout="vertical"
                  onFinish={(values) => applyTransition(pendingAction, values.notes)}
                >
                  <Form.Item
                    name="notes"
                    label={`Resolution Notes (${pendingAction === "CLOSED" ? "Close" : "Dismiss"} reason)`}
                    rules={[{ required: true, message: "Please fill out the reasons" }]}
                  >
                    <Input.TextArea rows={3} placeholder="Provide the resolution for this alert." />
                  </Form.Item>
                  <Space>
                    <Button type="primary" htmlType="submit" loading={updating}>
                      Confirm {pendingAction === "CLOSED" ? "close" : "dismiss"}
                    </Button>
                    <Button onClick={() => setPendingAction(null)}>Cancel</Button>
                  </Space>
                </Form>
              )}
            </div>
          )}
        </Spin>
      </Drawer>
    </div>
  );
}
