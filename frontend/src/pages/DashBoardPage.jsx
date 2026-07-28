import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Badge,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Input,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import {
  AlertTriangle,
  CheckCircle2,
  Clock,
  Eye,
  RefreshCw,
  Search,
  ShieldAlert,
  TrendingDown,
  TrendingUp,
  Wifi,
  WifiOff,
} from "lucide-react";

const { Text, Title } = Typography;

const THEME = {
  bg: "#F8FAFC",
  cardBg: "#FFFFFF",
  border: "#E2E8F0",
  textPrimary: "#0F172A",
  textSecondary: "#64748B",
  textMuted: "#94A3B8",
  shadow: "0 1px 3px rgba(15, 23, 42, 0.05)",
  high: { main: "#EF4444", bg: "#FEF2F2", border: "#FCA5A5" },
  medium: { main: "#F59E0B", bg: "#FFFBEB", border: "#FDE68A" },
  low: { main: "#64748B", bg: "#F1F5F9", border: "#CBD5E1" },
  success: { main: "#10B981", bg: "#ECFDF5", border: "#A7F3D0" },
  info: { main: "#3B82F6", bg: "#EFF6FF", border: "#BFDBFE" },
};

const SEVERITY_COLORS = {
  HIGH: THEME.high.main,
  MEDIUM: THEME.medium.main,
  LOW: THEME.low.main,
};

const STATUS_COLORS = {
  OPEN: THEME.high.main,
  ACKNOWLEDGED: THEME.medium.main,
  INVESTIGATING: THEME.info.main,
  CLOSED: THEME.success.main,
  DISMISSED: THEME.low.main,
};

const EMPTY_DASHBOARD = {
  generatedAt: null,
  summary: {
    openAlerts: 0,
    acknowledgedAlerts: 0,
    totalAlertsToday: 0,
    alertsTodayChangePercent: 0,
    averageResolutionMinutes: null,
    targetResolutionMinutes: 30,
  },
  transactionsOverTime: [],
  alertsBySeverity: [],
  alertStatusDistribution: [],
  alertResponseTimeTrend: [],
  recentAlerts: [],
};

const cardStyle = {
  background: THEME.cardBg,
  border: `1px solid ${THEME.border}`,
  borderRadius: 8,
  boxShadow: THEME.shadow,
};

const MonoText = ({ children, style = {} }) => (
  <span
    style={{
      fontFamily: "'IBM Plex Mono', 'SF Mono', Consolas, monospace",
      ...style,
    }}
  >
    {children}
  </span>
);

function mergeDefined(base, patch) {
  if (!patch) return base;
  return Object.entries(patch).reduce((next, [key, value]) => {
    if (value !== null && value !== undefined) next[key] = value;
    return next;
  }, { ...base });
}

function mergeDashboard(previous, incoming) {
  if (!incoming) return previous;
  const next = mergeDefined(previous, incoming);
  next.summary = mergeDefined(previous.summary, incoming.summary);
  return next;
}

function resolveDashboardWebSocketUrl() {
  if (import.meta.env.VITE_DASHBOARD_WS_URL) {
    return import.meta.env.VITE_DASHBOARD_WS_URL;
  }
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/dashboard`;
}

function useDashboardWebSocket() {
  const [dashboard, setDashboard] = useState(EMPTY_DASHBOARD);
  const [connectionState, setConnectionState] = useState("CONNECTING");
  const [loading, setLoading] = useState(true);
  const socketRef = useRef(null);

  useEffect(() => {
    let stopped = false;
    let reconnectTimer;
    let initialConnectTimer;

    const connect = () => {
      if (stopped) return;
      setConnectionState("CONNECTING");

      let socket;
      try {
        socket = new WebSocket(resolveDashboardWebSocketUrl());
      } catch {
        setConnectionState("DISCONNECTED");
        reconnectTimer = window.setTimeout(connect, 3_000);
        return;
      }

      socketRef.current = socket;
      socket.onopen = () => setConnectionState("CONNECTED");
      socket.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          if (!message?.type || !message?.data) return;
          setDashboard((previous) => mergeDashboard(previous, message.data));
          setLoading(false);
        } catch {
          // Ignore malformed messages without interrupting live updates.
        }
      };
      socket.onerror = () => setConnectionState("DISCONNECTED");
      socket.onclose = () => {
        if (socketRef.current === socket) socketRef.current = null;
        setConnectionState("DISCONNECTED");
        if (!stopped) reconnectTimer = window.setTimeout(connect, 3_000);
      };
    };

    // Deferring the first connection prevents a redundant handshake in React StrictMode.
    initialConnectTimer = window.setTimeout(connect, 0);
    return () => {
      stopped = true;
      window.clearTimeout(initialConnectTimer);
      window.clearTimeout(reconnectTimer);
      socketRef.current?.close(1000, "Dashboard unmounted");
      socketRef.current = null;
    };
  }, []);

  const refresh = useCallback(() => {
    if (socketRef.current?.readyState === WebSocket.OPEN) {
      socketRef.current.send("REFRESH");
    }
  }, []);

  return { dashboard, connectionState, loading, refresh };
}

function formatTimestamp(value) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--";
  return new Intl.DateTimeFormat(undefined, {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

function formatAlertId(id) {
  return `ALT-${String(id).padStart(4, "0")}`;
}

function TransactionsLineChart({ data }) {
  if (!data?.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No transaction data" />;
  }

  const width = 600;
  const height = 145;
  const left = 32;
  const right = 12;
  const top = 18;
  const bottom = 24;
  const maxCount = Math.max(1, ...data.map((point) => point.transactionCount ?? 0));
  const plotHeight = height - top - bottom;
  const plotWidth = width - left - right;
  const points = data.map((point, index) => ({
    ...point,
    x: left + (index / Math.max(1, data.length - 1)) * plotWidth,
    y: top + (1 - (point.transactionCount ?? 0) / maxCount) * plotHeight,
  }));
  const line = points.map((point) => `${point.x},${point.y}`).join(" ");
  const area = `${left},${height - bottom} ${line} ${width - right},${height - bottom}`;
  const peak = points.reduce((best, point) =>
    point.transactionCount > best.transactionCount ? point : best
  );
  const yTicks = [maxCount, Math.round(maxCount / 2), 0];
  const xTicks = [0, 4, 8, 12, 16, 20, 24];

  return (
    <svg width="100%" height="155" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Today's hourly transaction count">
      <defs>
        <linearGradient id="transactionCountGradient" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={THEME.info.main} stopOpacity="0.22" />
          <stop offset="100%" stopColor={THEME.info.main} stopOpacity="0" />
        </linearGradient>
      </defs>
      {yTicks.map((tick, index) => {
        const y = top + (index / 2) * plotHeight;
        return (
          <g key={`${tick}-${index}`}>
            <line x1={left} y1={y} x2={width - right} y2={y} stroke="#E8EEF5" strokeDasharray="4 4" />
            <text x={left - 7} y={y + 3} textAnchor="end" fontSize="9" fill={THEME.textMuted}>{tick}</text>
          </g>
        );
      })}
      <polygon points={area} fill="url(#transactionCountGradient)" />
      <polyline points={line} fill="none" stroke={THEME.info.main} strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={peak.x} cy={peak.y} r="4" fill="#fff" stroke={THEME.info.main} strokeWidth="2.5" />
      <text x={peak.x} y={Math.max(10, peak.y - 8)} textAnchor="middle" fontSize="10" fontWeight="700" fill={THEME.textSecondary}>
        {peak.transactionCount}
      </text>
      {xTicks.map((hour) => (
        <text
          key={hour}
          x={left + (hour / 24) * plotWidth}
          y={height - 5}
          textAnchor={hour === 0 ? "start" : hour === 24 ? "end" : "middle"}
          fontSize="9"
          fill={THEME.textMuted}
        >
          {String(hour).padStart(2, "0")}:00
        </text>
      ))}
    </svg>
  );
}

function SeverityBarChart({ data }) {
  const values = data?.length ? data : [
    { category: "HIGH", count: 0 },
    { category: "MEDIUM", count: 0 },
    { category: "LOW", count: 0 },
  ];
  const max = Math.max(1, ...values.map((item) => item.count ?? 0));

  return (
    <div style={{ height: 155, display: "flex", flexDirection: "column", justifyContent: "center", gap: 14 }}>
      {values.map((item) => (
        <div key={item.category} style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ width: 78, fontSize: 11, fontWeight: 600, color: THEME.textSecondary }}>{item.category}</span>
          <div style={{ flex: 1, height: 16, background: "#F1F5F9", borderRadius: 4, overflow: "hidden", padding: 2 }}>
            <div
              style={{
                width: `${((item.count ?? 0) / max) * 100}%`,
                minWidth: item.count > 0 ? 4 : 0,
                height: "100%",
                borderRadius: 2,
                background: SEVERITY_COLORS[item.category] ?? THEME.low.main,
                transition: "width 0.45s ease",
              }}
            />
          </div>
          <MonoText style={{ width: 34, textAlign: "right", fontSize: 12, fontWeight: 700, color: SEVERITY_COLORS[item.category] }}>
            {item.count ?? 0}
          </MonoText>
        </div>
      ))}
    </div>
  );
}

function StatusDonutChart({ data }) {
  const values = data ?? [];
  const total = values.reduce((sum, item) => sum + (item.count ?? 0), 0);
  let cumulative = 0;

  return (
    <div style={{ minHeight: 155, display: "flex", alignItems: "center", justifyContent: "space-around", gap: 24 }}>
      <div style={{ position: "relative", width: 112, height: 112, flexShrink: 0 }}>
        <svg width="100%" height="100%" viewBox="0 0 42 42" style={{ transform: "rotate(-90deg)" }} role="img" aria-label="Alert status distribution">
          <circle cx="21" cy="21" r="15.915" fill="transparent" stroke="#F1F5F9" strokeWidth="6" />
          {values.map((item) => {
            const percentage = total === 0 ? 0 : ((item.count ?? 0) / total) * 100;
            const offset = -cumulative;
            cumulative += percentage;
            return (
              <circle
                key={item.category}
                cx="21"
                cy="21"
                r="15.915"
                fill="transparent"
                stroke={STATUS_COLORS[item.category] ?? THEME.low.main}
                strokeWidth="6"
                strokeDasharray={`${Math.max(0, percentage - 0.8)} ${100 - Math.max(0, percentage - 0.8)}`}
                strokeDashoffset={offset}
              />
            );
          })}
        </svg>
        <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
          <span style={{ fontSize: 9, color: THEME.textMuted, fontWeight: 600 }}>TOTAL</span>
          <MonoText style={{ fontSize: 19, fontWeight: 700 }}>{total}</MonoText>
        </div>
      </div>
      <div style={{ minWidth: 185, display: "flex", flexDirection: "column", gap: 6 }}>
        {values.map((item) => {
          const percentage = total === 0 ? 0 : ((item.count ?? 0) / total) * 100;
          return (
            <div key={item.category} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, fontSize: 11 }}>
              <span style={{ display: "flex", alignItems: "center", gap: 6, color: THEME.textSecondary }}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: STATUS_COLORS[item.category] ?? THEME.low.main }} />
                {item.category}
              </span>
              <MonoText style={{ fontWeight: 700 }}>{percentage.toFixed(0)}%</MonoText>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function ResponseTimeTrendChart({ data, target }) {
  const values = data ?? [];
  const valid = values.filter((item) => item.averageMinutes !== null && item.averageMinutes !== undefined);
  if (valid.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No response-time data" />;
  }

  const width = 600;
  const height = 145;
  const left = 24;
  const right = 14;
  const top = 18;
  const bottom = 24;
  const ceiling = Math.max(target ?? 30, ...valid.map((item) => Number(item.averageMinutes)), 1) * 1.15;
  const xForIndex = (index) => left + (index / Math.max(1, values.length - 1)) * (width - left - right);
  const yForValue = (value) => top + (1 - Number(value) / ceiling) * (height - top - bottom);
  const points = valid.map((item) => {
    const index = values.indexOf(item);
    return { ...item, x: xForIndex(index), y: yForValue(item.averageMinutes) };
  });
  const targetY = yForValue(target ?? 30);

  return (
    <svg width="100%" height="155" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Seven-day alert response-time trend">
      <line x1={left} y1={targetY} x2={width - right} y2={targetY} stroke={THEME.high.main} strokeDasharray="5 5" opacity="0.65" />
      <text x={width - right} y={Math.max(10, targetY - 6)} textAnchor="end" fontSize="9" fontWeight="600" fill={THEME.high.main}>
        Target SLA: {(target ?? 30).toFixed(1)}m
      </text>
      <polyline
        points={points.map((point) => `${point.x},${point.y}`).join(" ")}
        fill="none"
        stroke={THEME.success.main}
        strokeWidth="2.5"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      {points.map((point) => (
        <g key={point.date}>
          <circle cx={point.x} cy={point.y} r="4" fill="#fff" stroke={THEME.success.main} strokeWidth="2.5" />
          <text x={point.x} y={Math.max(10, point.y - 8)} textAnchor="middle" fontSize="9" fontWeight="700" fill={THEME.success.main}>
            {Number(point.averageMinutes).toFixed(1)}m
          </text>
        </g>
      ))}
      {values.map((item, index) => (
        <text key={item.date} x={xForIndex(index)} y={height - 5} textAnchor="middle" fontSize="9" fill={THEME.textMuted}>
          {item.label}
        </text>
      ))}
    </svg>
  );
}

function SummaryCard({ title, value, icon, color, note, suffix, precision }) {
  return (
    <Card style={cardStyle} styles={{ body: { padding: "12px 16px" } }}>
      <Statistic
        title={<span style={{ fontSize: 10, fontWeight: 600, color: THEME.textMuted, letterSpacing: 0.5 }}>{title}</span>}
        value={value ?? "--"}
        precision={value === null || value === undefined ? undefined : precision}
        suffix={suffix}
        styles={{ content: { color, fontWeight: 700, fontFamily: "monospace", fontSize: 20 } }}
        prefix={icon}
      />
      <Text style={{ fontSize: 11, color: THEME.textSecondary, marginTop: 2, display: "block" }}>{note}</Text>
    </Card>
  );
}

export default function RiskDashboard() {
  const { dashboard, connectionState, loading, refresh } = useDashboardWebSocket();
  const [selectedAlert, setSelectedAlert] = useState(null);
  const [searchText, setSearchText] = useState("");
  const summary = dashboard.summary ?? EMPTY_DASHBOARD.summary;
  const connected = connectionState === "CONNECTED";
  const change = Number(summary.alertsTodayChangePercent ?? 0);

  const visibleAlerts = useMemo(() => {
    const normalized = searchText.trim().toLowerCase();
    if (!normalized) return dashboard.recentAlerts ?? [];
    return (dashboard.recentAlerts ?? []).filter((alert) =>
      alert.accountId?.toLowerCase().includes(normalized)
      || formatAlertId(alert.id).toLowerCase().includes(normalized)
    );
  }, [dashboard.recentAlerts, searchText]);

  const columns = [
    {
      title: "ALERT ID",
      dataIndex: "id",
      render: (id) => <MonoText style={{ fontWeight: 600 }}>{formatAlertId(id)}</MonoText>,
    },
    { title: "RULE TRIGGERED", dataIndex: "ruleName" },
    {
      title: "SEVERITY",
      dataIndex: "severity",
      render: (severity) => <Tag color={severity === "HIGH" ? "error" : severity === "MEDIUM" ? "warning" : "default"}>{severity}</Tag>,
    },
    {
      title: "ACCOUNT ID",
      dataIndex: "accountId",
      render: (value) => <MonoText style={{ color: THEME.textSecondary }}>{value}</MonoText>,
    },
    {
      title: "TRIGGER AMOUNT",
      dataIndex: "triggerAmount",
      align: "right",
      render: (value, row) => <MonoText style={{ fontWeight: 600 }}>{row.currency} {Number(value ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</MonoText>,
    },
    {
      title: "STATUS",
      dataIndex: "status",
      render: (status) => <Tag color={status === "OPEN" ? "error" : status === "ACKNOWLEDGED" ? "warning" : status === "INVESTIGATING" ? "processing" : status === "CLOSED" ? "success" : "default"}>{status}</Tag>,
    },
    {
      title: "TIMESTAMP",
      dataIndex: "createdAt",
      render: (value) => <MonoText style={{ color: THEME.textMuted, fontSize: 12 }}>{formatTimestamp(value)}</MonoText>,
    },
    {
      title: "ACTION",
      align: "right",
      render: (_, row) => (
        <Button type="text" size="small" icon={<Eye size={14} />} onClick={() => setSelectedAlert(row)}>
          Details
        </Button>
      ),
    },
  ];

  return (
    <Spin spinning={loading} description="Loading live dashboard data">
      <div style={{ background: THEME.bg, minHeight: "100vh", padding: "clamp(12px, 2vw, 20px) clamp(16px, 2.5vw, 24px)", fontFamily: "Inter, sans-serif" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 12, marginBottom: 14 }}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
              <Title level={4} style={{ margin: 0, color: THEME.textPrimary, fontWeight: 700, fontSize: 18 }}>Transaction Risk Monitoring</Title>
              <Badge status={connected ? "success" : "default"} text={<span style={{ fontSize: 12, color: connected ? THEME.success.main : THEME.textMuted }}>{connected ? "Live Engine Active" : "Live Engine Reconnecting"}</span>} />
            </div>
            <Text style={{ fontSize: 12, color: THEME.textSecondary }}>Real-time automated transaction evaluation and alert lifecycle triage</Text>
          </div>
          <Space size="small" wrap>
            <Tooltip title="Operations: 5s · Transactions: 15s · SLA: 60s">
              <span style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 11, color: connected ? THEME.success.main : THEME.textMuted }}>
                {connected ? <Wifi size={14} /> : <WifiOff size={14} />}
                {dashboard.generatedAt ? `Updated ${formatTimestamp(dashboard.generatedAt)}` : "Waiting for data"}
              </span>
            </Tooltip>
            <Button icon={<RefreshCw size={13} />} size="small" onClick={refresh} disabled={!connected}>Refresh</Button>
          </Space>
        </div>

        <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
          <Col xs={24} sm={12} lg={6}>
            <SummaryCard title="OPEN ALERTS" value={summary.openAlerts} color={THEME.high.main} icon={<ShieldAlert size={18} color={THEME.high.main} />} note="Requires immediate review" />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <SummaryCard title="ACKNOWLEDGED" value={summary.acknowledgedAlerts} color={THEME.medium.main} icon={<Clock size={18} color={THEME.medium.main} />} note="Under initial triage" />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <SummaryCard
              title="TOTAL ALERTS TODAY"
              value={summary.totalAlertsToday}
              color={THEME.textPrimary}
              icon={<AlertTriangle size={18} color={THEME.textSecondary} />}
              note={<span style={{ color: change >= 0 ? THEME.success.main : THEME.high.main, display: "inline-flex", alignItems: "center", gap: 4 }}>{change >= 0 ? <TrendingUp size={11} /> : <TrendingDown size={11} />}{change >= 0 ? "+" : ""}{change.toFixed(1)}% from yesterday</span>}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <SummaryCard
              title="AVG RESOLUTION SLA"
              value={summary.averageResolutionMinutes}
              precision={1}
              suffix={<span style={{ fontSize: 12 }}>mins</span>}
              color={THEME.textPrimary}
              icon={<CheckCircle2 size={18} color={THEME.success.main} />}
              note={`Target SLA: < ${Number(summary.targetResolutionMinutes ?? 30).toFixed(1)} mins`}
            />
          </Col>
        </Row>

        <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
          <Col xs={24} lg={12}>
            <Card size="small" title="Transactions Over Time" extra={<Text type="secondary" style={{ fontSize: 11 }}>Today · count by UTC hour</Text>} style={cardStyle}>
              <TransactionsLineChart data={dashboard.transactionsOverTime} />
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card size="small" title="Alerts by Severity" extra={<Text type="secondary" style={{ fontSize: 11 }}>Last 7 UTC days</Text>} style={cardStyle}>
              <SeverityBarChart data={dashboard.alertsBySeverity} />
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card size="small" title="Alert Status Distribution" extra={<Text type="secondary" style={{ fontSize: 11 }}>Last 7 UTC days</Text>} style={cardStyle}>
              <StatusDonutChart data={dashboard.alertStatusDistribution} />
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card size="small" title="Alert Response Time Trend" extra={<Text type="secondary" style={{ fontSize: 11 }}>First response · 7 days</Text>} style={cardStyle}>
              <ResponseTimeTrendChart data={dashboard.alertResponseTimeTrend} target={Number(summary.targetResolutionMinutes ?? 30)} />
            </Card>
          </Col>
        </Row>

        <div style={{ ...cardStyle, overflow: "hidden" }}>
          <div style={{ padding: "10px 16px", borderBottom: `1px solid ${THEME.border}`, display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 10, background: "#FAFAFA" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 600 }}>Real-Time Triggered Alerts</span>
              <Badge count={(dashboard.recentAlerts ?? []).length} showZero style={{ backgroundColor: THEME.textPrimary }} />
            </div>
            <Input value={searchText} onChange={(event) => setSearchText(event.target.value)} allowClear placeholder="Search Account / Alert ID" prefix={<Search size={13} color={THEME.textMuted} />} style={{ width: 220 }} size="small" />
          </div>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={visibleAlerts}
            size="small"
            pagination={false}
            scroll={{ x: "max-content" }}
            onRow={(row) => ({ onClick: () => setSelectedAlert(row), style: { cursor: "pointer" } })}
          />
        </div>

        <Drawer title="Alert Details" size="large" open={Boolean(selectedAlert)} onClose={() => setSelectedAlert(null)}>
          {selectedAlert && (
            <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <div style={{ background: THEME.bg, padding: 14, borderRadius: 8, border: `1px solid ${THEME.border}` }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
                  <MonoText style={{ fontWeight: 700 }}>{formatAlertId(selectedAlert.id)}</MonoText>
                  <Tag color={selectedAlert.status === "OPEN" ? "error" : "processing"}>{selectedAlert.status}</Tag>
                </div>
                <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 4 }}>{selectedAlert.title || selectedAlert.ruleName}</div>
                <div style={{ fontSize: 12, color: THEME.textSecondary }}>{selectedAlert.description || "No additional description"}</div>
              </div>
              {[
                ["Rule", selectedAlert.ruleName],
                ["Severity", selectedAlert.severity],
                ["Account ID", selectedAlert.accountId],
                ["Trigger amount", `${selectedAlert.currency} ${Number(selectedAlert.triggerAmount ?? 0).toLocaleString(undefined, { minimumFractionDigits: 2 })}`],
                ["Created at", formatTimestamp(selectedAlert.createdAt)],
              ].map(([label, value]) => (
                <div key={label} style={{ display: "flex", justifyContent: "space-between", gap: 20, fontSize: 12 }}>
                  <span style={{ color: THEME.textSecondary }}>{label}</span>
                  <MonoText style={{ textAlign: "right", fontWeight: 600 }}>{value}</MonoText>
                </div>
              ))}
              <Text type="secondary" style={{ fontSize: 11 }}>Use the Alerts page to acknowledge, investigate, close, or dismiss this alert.</Text>
            </div>
          )}
        </Drawer>
      </div>
    </Spin>
  );
}
