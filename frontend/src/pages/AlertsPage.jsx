import { useMemo, useState } from "react";
import { Table, Tag, DatePicker, Drawer, Descriptions, Button, Space, Form, Input, Timeline, message } from "antd";
import { COLORS } from "../constants/theme";
import {
    ALERTS,
    TRANSACTIONS,
    RULE_NAME_BY_ID,
    SEVERITY_OPTIONS,
    SEVERITY_COLOR,
    ALERT_STATUS_OPTIONS,
    ALERT_STATUS_COLOR,
    TODAY,
} from "../constants/mockData";
import AlertSummaryCard from "../components/AlertSummaryCard";

const { RangePicker } = DatePicker;

function toDate(ts) {
    return new Date(ts.replace(" ", "T"));
}

function formatDuration(minutes) {
    if (minutes == null || Number.isNaN(minutes)) return "—";
    if (minutes < 60) return `${Math.round(minutes)} mins`;
    const h = Math.floor(minutes / 60);
    const m = Math.round(minutes % 60);
    return `${h} hours ${m} mins`;
}

// which actions are allowed from each status.
// Per the lifecycle diagram, DISMISSED is only reachable from ACKNOWLEDGED or
// INVESTIGATING — OPEN can only move forward to ACKNOWLEDGED.
const NEXT_ACTIONS = {
    OPEN: [{ to: "ACKNOWLEDGED", label: "Acknowledge", type: "primary" }],
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

const CURRENT_USER = "you"; // placeholder for the logged-in operator

export default function AlertsPage() {
    const [alerts, setAlerts] = useState(ALERTS);
    const [dateRange, setDateRange] = useState(null);
    const [selected, setSelected] = useState(null);
    const [pendingAction, setPendingAction] = useState(null); // { to } when resolution notes are required
    const [form] = Form.useForm();

    const openCount = alerts.filter((a) => a.status === "OPEN").length;
    const ackCount = alerts.filter((a) => a.status === "ACKNOWLEDGED").length;
    const todayCount = alerts.filter((a) => a.createdAt.startsWith(TODAY)).length;
    const avgResolutionMinutes = useMemo(() => {
        const resolved = alerts.filter((a) => a.closedAt);
        if (resolved.length === 0) return null;
        const total = resolved.reduce((sum, a) => sum + (toDate(a.closedAt) - toDate(a.createdAt)) / 60000, 0);
        return total / resolved.length;
    }, [alerts]);

    const filteredData = useMemo(() => {
        if (!dateRange) return alerts;
        const [start, end] = dateRange;
        return alerts.filter((a) => {
            const created = toDate(a.createdAt);
            return created >= start.toDate() && created <= end.toDate();
        });
    }, [alerts, dateRange]);

    const columns = [
        {
            title: "Alert ID",
            dataIndex: "key",
            key: "key",
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
            filters: SEVERITY_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
            onFilter: (value, row) => row.severity === value,
            render: (s) => <Tag color={SEVERITY_COLOR[s]}>{s}</Tag>,
        },
        {
            title: "Rule Name",
            dataIndex: "ruleId",
            key: "ruleId",
            render: (ruleId) => <span style={{ fontSize: 13, color: COLORS.ink }}>{RULE_NAME_BY_ID[ruleId] ?? ruleId}</span>,
        },
        {
            title: "Status",
            dataIndex: "status",
            key: "status",
            filters: ALERT_STATUS_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
            onFilter: (value, row) => row.status === value,
            render: (s) => <Tag color={ALERT_STATUS_COLOR[s]}>{s}</Tag>,
        },
        {
            title: "Created Time",
            dataIndex: "createdAt",
            key: "createdAt",
            sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
            defaultSortOrder: "descend",
            render: (v) => (
                <span style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: 13, color: COLORS.slate }}>{v}</span>
            ),
        },
    ];

    const openDetail = (row) => {
        setSelected(row);
        setPendingAction(null);
        form.resetFields();
    };

    const applyTransition = (to, notes) => {
        setAlerts((prev) =>
            prev.map((a) => {
                if (a.key !== selected.key) return a;
                const now = "2026-07-27 " + new Date().toTimeString().slice(0, 8);
                if (to === "ACKNOWLEDGED") {
                    return { ...a, status: to, acknowledgedAt: now, acknowledgedBy: CURRENT_USER };
                }
                if (to === "INVESTIGATING") {
                    return { ...a, status: to, investigatingAt: now, investigatingBy: CURRENT_USER };
                }
                if (to === "CLOSED" || to === "DISMISSED") {
                    return { ...a, status: to, closedAt: now, closedBy: CURRENT_USER, resolutionNotes: notes ?? a.resolutionNotes };
                }
                return { ...a, status: to };
            })
        );
        message.success(`Alert has been updated to ${to}`);
        setSelected((prev) => (prev ? { ...prev, status: to } : prev));
        setPendingAction(null);
        form.resetFields();
    };

    const handleActionClick = (action) => {
        if (action.to === "CLOSED" || action.to === "DISMISSED") {
            setPendingAction(action.to);
        } else {
            applyTransition(action.to);
        }
    };

    const relatedTransactions = selected
        ? TRANSACTIONS.filter((t) => selected.transactionIds.includes(t.key))
        : [];

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

            <div>
                <RangePicker
                    onChange={(v) => setDateRange(v)}
                    placeholder={["Created from", "Created to"]}
                    style={{ maxWidth: 320 }}
                />
            </div>

            <div style={{ background: COLORS.card, border: `1px solid ${COLORS.border}`, borderRadius: 12, overflow: "hidden" }}>
                <Table
                    columns={columns}
                    dataSource={filteredData}
                    size="middle"
                    onRow={(row) => ({ onClick: () => openDetail(row), style: { cursor: "pointer" } })}
                    pagination={{ pageSize: 6, showTotal: (total) => `${total} Alerts` }}
                />
            </div>

            <Drawer
                title={selected?.key}
                open={!!selected}
                onClose={() => setSelected(null)}
                width={480}
            >
                {selected && (
                    <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
                        <Descriptions column={1} size="small" bordered>
                            <Descriptions.Item label="Rule Name">{RULE_NAME_BY_ID[selected.ruleId] ?? selected.ruleId}</Descriptions.Item>
                            <Descriptions.Item label="Severity">
                                <Tag color={SEVERITY_COLOR[selected.severity]}>{selected.severity}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="Status">
                                <Tag color={ALERT_STATUS_COLOR[selected.status]}>{selected.status}</Tag>
                            </Descriptions.Item>
                            <Descriptions.Item label="Created">{selected.createdAt}</Descriptions.Item>
                        </Descriptions>

                        <div>
                            <div style={{ fontSize: 13, fontWeight: 600, color: COLORS.ink, marginBottom: 10 }}>Alert Status History</div>
                            <Timeline
                                items={[
                                    { color: ALERT_STATUS_COLOR.OPEN, children: `OPEN · ${selected.createdAt}` },
                                    selected.acknowledgedAt && {
                                        color: ALERT_STATUS_COLOR.ACKNOWLEDGED,
                                        children: `ACKNOWLEDGED · ${selected.acknowledgedAt} · ${selected.acknowledgedBy}`,
                                    },
                                    selected.investigatingAt && {
                                        color: ALERT_STATUS_COLOR.INVESTIGATING,
                                        children: `INVESTIGATING · ${selected.investigatingAt} · ${selected.investigatingBy}`,
                                    },
                                    selected.closedAt && {
                                        color: ALERT_STATUS_COLOR[selected.status],
                                        children: (
                                            <div>
                                                <div>{`${selected.status} · ${selected.closedAt} · ${selected.closedBy}`}</div>
                                                {selected.resolutionNotes && (
                                                    <div style={{ fontSize: 12, color: COLORS.slate, marginTop: 4 }}>
                                                        Resolution Notes: {selected.resolutionNotes}
                                                    </div>
                                                )}
                                            </div>
                                        ),
                                    },
                                ].filter(Boolean)}
                            />
                        </div>

                        <div>
                            <div style={{ fontSize: 13, fontWeight: 600, color: COLORS.ink, marginBottom: 8 }}>触发交易</div>
                            <Table
                                size="small"
                                pagination={false}
                                dataSource={relatedTransactions}
                                columns={[
                                    { title: "Transaction ID", dataIndex: "key" },
                                    { title: "Payee", dataIndex: "counterpartyId" },
                                    {
                                        title: "Amount",
                                        dataIndex: "amount",
                                        align: "right",
                                        render: (v, row) => `${row.type === "DEBIT" ? "-" : "+"}${v.toFixed(2)} ${row.currency}`,
                                    },
                                    { title: "Time", dataIndex: "timestamp" },
                                ]}
                            />
                        </div>

                        {NEXT_ACTIONS[selected.status].length > 0 && !pendingAction && (
                            <Space>
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
                                    label={`Resolution Notes(${pendingAction === "CLOSED" ? "Close" : "Dismiss"} reason)`}
                                    rules={[{ required: true, message: "Please fill out the reasons" }]}
                                >
                                    <Input.TextArea rows={3} placeholder="Provide the resolution for this alert." />
                                </Form.Item>
                                <Space>
                                    <Button type="primary" htmlType="submit">
                                        confirm{pendingAction === "CLOSED" ? " close" : " dismiss"}
                                    </Button>
                                    <Button onClick={() => setPendingAction(null)}>cancel</Button>
                                </Space>
                            </Form>
                        )}
                    </div>
                )}
            </Drawer>
        </div>
    );
}
