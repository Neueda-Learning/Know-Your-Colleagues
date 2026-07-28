import React, { useState } from 'react';
import { Row, Col, Card, Statistic, Table, Tag, Button, Drawer, Space, Typography, Badge, Input } from 'antd';
import {
    ShieldAlert,
    Clock,
    AlertTriangle,
    CheckCircle2,
    TrendingUp,
    Eye,
    SlidersHorizontal,
    Search,
    RefreshCw,
    ArrowUpRight
} from 'lucide-react';

const { Text, Title } = Typography;

// --- 基础样式与色彩常量 ---
const THEME = {
    bg: '#F8FAFC',
    cardBg: '#FFFFFF',
    border: '#E2E8F0',
    textPrimary: '#0F172A',
    textSecondary: '#64748B',
    textMuted: '#94A3B8',
    shadow: '0 1px 3px 0 rgba(15, 23, 42, 0.03), 0 1px 2px -1px rgba(15, 23, 42, 0.03)',

    high: { main: '#EF4444', bg: '#FEF2F2', border: '#FCA5A5' },
    medium: { main: '#F59E0B', bg: '#FFFBEB', border: '#FDE68A' },
    low: { main: '#64748B', bg: '#F1F5F9', border: '#CBD5E1' },
    success: { main: '#10B981', bg: '#ECFDF5', border: '#A7F3D0' },
    info: { main: '#3B82F6', bg: '#EFF6FF', border: '#BFDBFE' },
};

const MonoText = ({ children, style = {} }) => (
    <span style={{ fontFamily: "'IBM Plex Mono', 'SF Mono', Consolas, monospace", ...style }}>
    {children}
  </span>
);

const INITIAL_STATS = {
    openAlerts: 12,
    acknowledgedAlerts: 8,
    todayTotalAlerts: 34,
    avgResolutionTimeMins: 14.5,
};

const ALERT_STATUS_MAP = {
    OPEN: { label: 'OPEN', color: THEME.high },
    ACKNOWLEDGED: { label: 'ACKNOWLEDGED', color: THEME.medium },
    INVESTIGATING: { label: 'INVESTIGATING', color: THEME.info },
    CLOSED: { label: 'CLOSED', color: THEME.success },
    DISMISSED: { label: 'DISMISSED', color: THEME.low },
};

const INITIAL_ALERTS = [
    { key: 'ALT-1092', ruleName: 'High Value Transaction', severity: 'HIGH', status: 'OPEN', accountId: 'ACC-883912', amount: 15400.0, timestamp: '2026-07-27 14:32:01', description: 'Transaction exceeds $10,000 threshold' },
    { key: 'ALT-1091', ruleName: 'Rapid Velocity Limit', severity: 'HIGH', status: 'ACKNOWLEDGED', accountId: 'ACC-102934', amount: 420.0, timestamp: '2026-07-27 14:18:22', description: '6 transactions detected within 10 minutes' },
    { key: 'ALT-1090', ruleName: 'New Counterparty Payee', severity: 'MEDIUM', status: 'INVESTIGATING', accountId: 'ACC-554109', amount: 3200.5, timestamp: '2026-07-27 13:55:10', description: 'First transfer to unverified payee PAYEE-991' },
    { key: 'ALT-1089', ruleName: 'Cumulative Daily Limit', severity: 'HIGH', status: 'OPEN', accountId: 'ACC-883912', amount: 52000.0, timestamp: '2026-07-27 13:12:44', description: 'Cumulative daily volume $50,000 exceeded' },
    { key: 'ALT-1088', ruleName: 'Off-hours High Value', severity: 'MEDIUM', status: 'CLOSED', accountId: 'ACC-331092', amount: 11200.0, timestamp: '2026-07-27 11:04:15', description: 'Legitimate corporate transfer verified by operator' },
];

// ==========================================
// 重构后的图表组件 (交易图表去除$、k，纯数字展示)
// ==========================================

// 1. 交易趋势折线图（按小时统计交易量，移除所有$、k单位）
const TransactionsLineChart = () => (
    <div style={{ width: '100%', height: 135, position: 'relative', display: 'flex', flexDirection: 'column', padding: '4px 0 0' }}>
        <div style={{ flex: 1, width: '100%', position: 'relative', overflow: 'hidden' }}>
            <svg width="100%" height="100%" viewBox="0 0 500 120" preserveAspectRatio="none" style={{ overflow: 'hidden' }}>
                <defs>
                    <linearGradient id="transGrad" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#3B82F6" stopOpacity="0.22" />
                        <stop offset="100%" stopColor="#3B82F6" stopOpacity="0.00" />
                    </linearGradient>
                </defs>

                {/* Y轴交易量刻度，已移除k单位 */}
                <line x1="0" y1="20" x2="500" y2="20" stroke="#F1F5F9" strokeDasharray="3 3" />
                <text x="6" y="24" fontSize="9" fill={THEME.textMuted} fontFamily="monospace">700</text>

                <line x1="0" y1="60" x2="500" y2="60" stroke="#F1F5F9" strokeDasharray="3 3" />
                <text x="6" y="64" fontSize="9" fill={THEME.textMuted} fontFamily="monospace">300</text>

                <line x1="0" y1="100" x2="500" y2="100" stroke="#F1F5F9" strokeDasharray="3 3" />
                <text x="6" y="104" fontSize="9" fill={THEME.textMuted} fontFamily="monospace">0</text>

                {/* 区域填充与平滑曲线 */}
                <path d="M 15,95 C 70,80 110,50 160,42 C 200,35 240,75 280,60 C 310,48 330,22 360,22 C 400,22 430,85 485,90 L 485,115 L 15,115 Z" fill="url(#transGrad)" />
                <path d="M 15,95 C 70,80 110,50 160,42 C 200,35 240,75 280,60 C 310,48 330,22 360,22 C 400,22 430,85 485,90" fill="none" stroke="#3B82F6" strokeWidth="2.5" strokeLinecap="round" />

                {/* 数据标注点：删除$和k，仅纯数字 */}
                {[{x: 160, y: 42, val: '510'}, {x: 360, y: 22, val: '680'}].map((pt, i) => (
                    <g key={i}>
                        <circle cx={pt.x} cy={pt.y} r="4" fill="#FFFFFF" stroke="#3B82F6" strokeWidth="2.5" />
                        <text x={pt.x} y={pt.y - 7} fontSize="10" textAnchor="middle" fill={THEME.textSecondary} fontFamily="monospace" fontWeight="700">
                            {pt.val}
                        </text>
                    </g>
                ))}
            </svg>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 2, padding: '0 6px', fontSize: 10, color: THEME.textMuted, fontFamily: 'monospace' }}>
            <span>00:00</span><span>04:00</span><span>08:00</span><span>12:00</span><span>16:00</span><span>20:00</span><span>24:00</span>
        </div>
    </div>
);

// 2. 告警等级柱状图
const SeverityBarChart = () => {
    const bars = [
        { label: 'HIGH', count: 18, color: THEME.high.main },
        { label: 'MEDIUM', count: 24, color: THEME.medium.main },
        { label: 'LOW', count: 12, color: THEME.low.main },
    ];
    const max = 30;

    return (
        <div style={{ width: '100%', height: 135, display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 12, padding: '0 4px' }}>
            {bars.map((item, idx) => (
                <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span style={{ width: 55, fontSize: 11, fontWeight: 600, color: THEME.textSecondary, flexShrink: 0 }}>{item.label}</span>
                    <div style={{ flex: 1, height: 16, backgroundColor: '#F1F5F9', borderRadius: 4, overflow: 'hidden', padding: 2 }}>
                        <div
                            style={{
                                width: `${(item.count / max) * 100}%`,
                                height: '100%',
                                backgroundColor: item.color,
                                borderRadius: 2,
                                transition: 'width 0.6s ease',
                            }}
                        />
                    </div>
                    <MonoText style={{ width: 24, fontSize: 12, fontWeight: 700, color: item.color, textAlign: 'right', flexShrink: 0 }}>
                        {item.count}
                    </MonoText>
                </div>
            ))}
        </div>
    );
};

// 3. 告警状态环形图
const StatusPieChart = () => (
    <div style={{ width: '100%', height: 135, display: 'flex', alignItems: 'center', justifyContent: 'space-around', padding: '0 8px' }}>
        <div style={{ position: 'relative', width: 100, height: 100, flexShrink: 0 }}>
            <svg width="100%" height="100%" viewBox="0 0 42 42" style={{ transform: 'rotate(-90deg)', transformOrigin: '50% 50%' }}>
                {/* 背景灰色槽线 */}
                <circle cx="21" cy="21" r="15.915" fill="transparent" stroke="#F1F5F9" strokeWidth="6" />

                {/* 4 个粗颗粒分类色彩弧线 */}
                <circle cx="21" cy="21" r="15.915" fill="transparent" stroke={THEME.high.main} strokeWidth="6" strokeDasharray="23 77" strokeDashoffset="0" />
                <circle cx="21" cy="21" r="15.915" fill="transparent" stroke={THEME.medium.main} strokeWidth="6" strokeDasharray="18 82" strokeDashoffset="-25" />
                <circle cx="21" cy="21" r="15.915" fill="transparent" stroke={THEME.info.main} strokeWidth="6" strokeDasharray="13 87" strokeDashoffset="-45" />
                <circle cx="21" cy="21" r="15.915" fill="transparent" stroke={THEME.success.main} strokeWidth="6" strokeDasharray="38 62" strokeDashoffset="-60" />
            </svg>

            {/* 环形内部中心文案 */}
            <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                <span style={{ fontSize: 9, color: THEME.textMuted, fontWeight: 600, letterSpacing: 0.5 }}>TOTAL</span>
                <MonoText style={{ fontSize: 18, fontWeight: 700, color: THEME.textPrimary, lineHeight: 1.1 }}>48</MonoText>
            </div>
        </div>

        {/* 右侧 Legend 数据项 */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 11, minWidth: 140 }}>
            {[
                { label: 'OPEN', pct: '25%', color: THEME.high.main },
                { label: 'ACKNOWLEDGED', pct: '20%', color: THEME.medium.main },
                { label: 'INVESTIGATING', pct: '15%', color: THEME.info.main },
                { label: 'CLOSED', pct: '40%', color: THEME.success.main },
            ].map((item, i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ width: 8, height: 8, borderRadius: 2, background: item.color, flexShrink: 0 }} />
                        <span style={{ color: THEME.textSecondary, fontSize: 11, fontWeight: 500 }}>{item.label}</span>
                    </div>
                    <MonoText style={{ fontWeight: 700, color: THEME.textPrimary, fontSize: 11 }}>{item.pct}</MonoText>
                </div>
            ))}
        </div>
    </div>
);

// 4. 响应时长趋势图
const ResponseTimeTrendChart = () => (
    <div style={{ width: '100%', height: 135, position: 'relative', display: 'flex', flexDirection: 'column', padding: '4px 0 0' }}>
        <div style={{ flex: 1, width: '100%', position: 'relative', overflow: 'hidden' }}>
            <svg width="100%" height="100%" viewBox="0 0 500 120" preserveAspectRatio="none" style={{ overflow: 'hidden' }}>
                {/* SLA 警戒上限虚线 */}
                <line x1="0" y1="28" x2="500" y2="28" stroke="#EF4444" strokeDasharray="4 4" strokeWidth="1.2" opacity="0.6" />
                <text x="490" y="20" fontSize="9" textAnchor="end" fill="#EF4444" fontFamily="monospace" fontWeight="600">Target SLA: 30.0m</text>

                {/* 下滑趋势曲线 */}
                <path d="M 20,48 C 100,70 180,55 260,75 C 340,90 410,95 480,98" fill="none" stroke={THEME.success.main} strokeWidth="2.5" strokeLinecap="round" />

                {[
                    { x: 20, y: 48, val: '22.4m' },
                    { x: 180, y: 55, val: '19.5m' },
                    { x: 340, y: 90, val: '14.5m' },
                    { x: 480, y: 98, val: '10.8m' },
                ].map((pt, i) => (
                    <g key={i}>
                        <circle cx={pt.x} cy={pt.y} r="4" fill="#FFFFFF" stroke={THEME.success.main} strokeWidth="2.5" />
                        <text x={pt.x} y={pt.y - 7} fontSize="10" textAnchor="middle" fill={THEME.success.main} fontFamily="monospace" fontWeight="700">
                            {pt.val}
                        </text>
                    </g>
                ))}
            </svg>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 2, padding: '0 6px', fontSize: 10, color: THEME.textMuted, fontFamily: 'monospace' }}>
            <span>Mon</span><span>Tue</span><span>Wed</span><span>Thu</span><span>Fri</span><span>Sat</span><span>Sun</span>
        </div>
    </div>
);

// ==========================================
// 主页面整体组件
// ==========================================
export default function RiskDashboard() {
    const [alerts, setAlerts] = useState(INITIAL_ALERTS);
    const [selectedAlert, setSelectedAlert] = useState(null);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);

    const handleViewAlert = (record) => {
        setSelectedAlert(record);
        setIsDrawerOpen(true);
    };

    const handleUpdateStatus = (newStatus) => {
        if (!selectedAlert) return;
        setAlerts((prev) =>
            prev.map((a) => (a.key === selectedAlert.key ? { ...a, status: newStatus } : a))
        );
        setSelectedAlert((prev) => ({ ...prev, status: newStatus }));
    };

    const cardStyle = {
        background: THEME.cardBg,
        border: `1px solid ${THEME.border}`,
        borderRadius: 8,
        boxShadow: THEME.shadow,
    };

    const columns = [
        {
            title: 'ALERT ID',
            dataIndex: 'key',
            key: 'key',
            render: (v) => <MonoText style={{ fontWeight: 600, color: THEME.textPrimary }}>{v}</MonoText>,
        },
        {
            title: 'RULE TRIGGERED',
            dataIndex: 'ruleName',
            key: 'ruleName',
            render: (v) => <span style={{ fontSize: 13, fontWeight: 500, color: THEME.textPrimary }}>{v}</span>,
        },
        {
            title: 'SEVERITY',
            dataIndex: 'severity',
            key: 'severity',
            render: (sev) => {
                const conf = sev === 'HIGH' ? THEME.high : sev === 'MEDIUM' ? THEME.medium : THEME.low;
                return (
                    <span style={{ background: conf.bg, color: conf.main, border: `1px solid ${conf.border}`, padding: '2px 8px', borderRadius: 4, fontSize: 11, fontWeight: 600, fontFamily: 'monospace' }}>
            {sev}
          </span>
                );
            },
        },
        {
            title: 'ACCOUNT ID',
            dataIndex: 'accountId',
            key: 'accountId',
            render: (v) => <MonoText style={{ color: THEME.textSecondary }}>{v}</MonoText>,
        },
        {
            title: 'TRIGGER AMOUNT',
            dataIndex: 'amount',
            key: 'amount',
            align: 'right',
            render: (v) => (
                <MonoText style={{ fontWeight: 600, color: THEME.textPrimary }}>
                    ${v.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                </MonoText>
            ),
        },
        {
            title: 'STATUS',
            dataIndex: 'status',
            key: 'status',
            render: (status) => {
                const conf = ALERT_STATUS_MAP[status]?.color || THEME.low;
                return (
                    <span style={{ background: conf.bg, color: conf.main, border: `1px solid ${conf.border}`, padding: '2px 8px', borderRadius: 12, fontSize: 11, fontWeight: 600 }}>
            {status}
          </span>
                );
            },
        },
        {
            title: 'TIMESTAMP',
            dataIndex: 'timestamp',
            key: 'timestamp',
            render: (v) => <MonoText style={{ color: THEME.textMuted, fontSize: 12 }}>{v}</MonoText>,
        },
        {
            title: 'ACTION',
            key: 'action',
            align: 'right',
            render: (_, record) => (
                <Button
                    type="text"
                    size="small"
                    icon={<Eye size={14} color={THEME.textSecondary} />}
                    onClick={(e) => {
                        e.stopPropagation();
                        handleViewAlert(record);
                    }}
                    style={{ color: THEME.textSecondary, fontSize: 12 }}
                >
                    Details
                </Button>
            ),
        },
    ];

    return (
        <div style={{ background: THEME.bg, minHeight: '100vh', padding: 'clamp(12px, 2vw, 20px) clamp(16px, 2.5vw, 24px)', fontFamily: 'Inter, sans-serif' }}>

            {/* 顶部 Header - 已移除Filter Rules按钮 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 14 }}>
                <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                        <Title level={4} style={{ margin: 0, color: THEME.textPrimary, fontWeight: 700, fontSize: 18 }}>
                            Transaction Risk Monitoring
                        </Title>
                        <Badge status="processing" text={<span style={{ fontSize: 12, color: THEME.success.main, fontWeight: 500 }}>Live Engine Active</span>} />
                    </div>
                    <Text style={{ fontSize: 12, color: THEME.textSecondary }}>Real-time automated transaction evaluation & alert lifecycle triage</Text>
                </div>
                {/* 仅保留Refresh按钮，删除Filter Rules */}
                <Space size="small" style={{ flexWrap: 'wrap' }}>
                    <Button icon={<RefreshCw size={13} />} size="small" style={{ borderRadius: 6 }}>Refresh</Button>
                </Space>
            </div>

            {/* 1. 顶部 4 个 KPI 卡片 */}
            <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
                <Col xs={24} sm={12} lg={6}>
                    <Card style={cardStyle} bodyStyle={{ padding: '12px 16px' }}>
                        <Statistic
                            title={<span style={{ fontSize: 10, fontWeight: 600, color: THEME.textMuted, letterSpacing: 0.5 }}>OPEN ALERTS</span>}
                            value={INITIAL_STATS.openAlerts}
                            valueStyle={{ color: THEME.high.main, fontWeight: 700, fontFamily: 'monospace', fontSize: 20 }}
                            prefix={<ShieldAlert size={18} color={THEME.high.main} style={{ marginRight: 6 }} />}
                        />
                        <Text style={{ fontSize: 11, color: THEME.textSecondary, marginTop: 2, display: 'block' }}>
                            Requires immediate review
                        </Text>
                    </Card>
                </Col>

                <Col xs={24} sm={12} lg={6}>
                    <Card style={cardStyle} bodyStyle={{ padding: '12px 16px' }}>
                        <Statistic
                            title={<span style={{ fontSize: 10, fontWeight: 600, color: THEME.textMuted, letterSpacing: 0.5 }}>ACKNOWLEDGED</span>}
                            value={INITIAL_STATS.acknowledgedAlerts}
                            valueStyle={{ color: THEME.medium.main, fontWeight: 700, fontFamily: 'monospace', fontSize: 20 }}
                            prefix={<Clock size={18} color={THEME.medium.main} style={{ marginRight: 6 }} />}
                        />
                        <Text style={{ fontSize: 11, color: THEME.textSecondary, marginTop: 2, display: 'block' }}>
                            Under initial triage
                        </Text>
                    </Card>
                </Col>

                <Col xs={24} sm={12} lg={6}>
                    <Card style={cardStyle} bodyStyle={{ padding: '12px 16px' }}>
                        <Statistic
                            title={<span style={{ fontSize: 10, fontWeight: 600, color: THEME.textMuted, letterSpacing: 0.5 }}>TOTAL ALERTS TODAY</span>}
                            value={INITIAL_STATS.todayTotalAlerts}
                            valueStyle={{ color: THEME.textPrimary, fontWeight: 700, fontFamily: 'monospace', fontSize: 20 }}
                            prefix={<AlertTriangle size={18} color={THEME.textSecondary} style={{ marginRight: 6 }} />}
                        />
                        <Text style={{ fontSize: 11, color: THEME.success.main, marginTop: 2, display: 'flex', alignItems: 'center', gap: 4 }}>
                            <TrendingUp size={11} /> +12% from yesterday
                        </Text>
                    </Card>
                </Col>

                <Col xs={24} sm={12} lg={6}>
                    <Card style={cardStyle} bodyStyle={{ padding: '12px 16px' }}>
                        <Statistic
                            title={<span style={{ fontSize: 10, fontWeight: 600, color: THEME.textMuted, letterSpacing: 0.5 }}>AVG RESOLUTION SLA</span>}
                            value={INITIAL_STATS.avgResolutionTimeMins}
                            precision={1}
                            suffix={<span style={{ fontSize: 12 }}>mins</span>}
                            valueStyle={{ color: THEME.textPrimary, fontWeight: 700, fontFamily: 'monospace', fontSize: 20 }}
                            prefix={<CheckCircle2 size={18} color={THEME.success.main} style={{ marginRight: 6 }} />}
                        />
                        <Text style={{ fontSize: 11, color: THEME.textSecondary, marginTop: 2, display: 'block' }}>
                            Target SLA: &lt; 30.0 mins
                        </Text>
                    </Card>
                </Col>
            </Row>

            {/* 2. 核心四大图表区域 */}
            <Row gutter={[12, 12]} style={{ marginBottom: 12 }}>
                <Col xs={24} lg={12}>
                    <Card
                        size="small"
                        title={<span style={{ fontSize: 13, fontWeight: 600, color: THEME.textPrimary }}>Transactions Over Time</span>}
                        style={cardStyle}
                        bodyStyle={{ padding: '8px 12px 10px' }}
                    >
                        <TransactionsLineChart />
                    </Card>
                </Col>

                <Col xs={24} lg={12}>
                    <Card
                        size="small"
                        title={<span style={{ fontSize: 13, fontWeight: 600, color: THEME.textPrimary }}>Alerts by Severity</span>}
                        style={cardStyle}
                        bodyStyle={{ padding: '8px 12px 10px' }}
                    >
                        <SeverityBarChart />
                    </Card>
                </Col>

                <Col xs={24} lg={12}>
                    <Card
                        size="small"
                        title={<span style={{ fontSize: 13, fontWeight: 600, color: THEME.textPrimary }}>Alert Status Distribution</span>}
                        style={cardStyle}
                        bodyStyle={{ padding: '8px 12px 10px' }}
                    >
                        <StatusPieChart />
                    </Card>
                </Col>

                <Col xs={24} lg={12}>
                    <Card
                        size="small"
                        title={<span style={{ fontSize: 13, fontWeight: 600, color: THEME.textPrimary }}>Alert Response Time Trend</span>}
                        style={cardStyle}
                        bodyStyle={{ padding: '8px 12px 10px' }}
                    >
                        <ResponseTimeTrendChart />
                    </Card>
                </Col>
            </Row>

            {/* 3. 告警明细 Table */}
            <div style={{ ...cardStyle, overflow: 'hidden' }}>
                <div style={{ padding: '10px 16px', borderBottom: `1px solid ${THEME.border}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10, background: '#FAFAFA' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontSize: 13, fontWeight: 600, color: THEME.textPrimary }}>Real-Time Triggered Alerts</span>
                        <Badge count={alerts.length} style={{ backgroundColor: THEME.textPrimary }} />
                    </div>
                    <Space style={{ flexWrap: 'wrap' }}>
                        <Input placeholder="Search Account / Alert ID" prefix={<Search size={13} color={THEME.textMuted} />} style={{ width: 200, borderRadius: 6 }} size="small" />
                        <Button type="link" size="small" style={{ fontSize: 12, color: THEME.textSecondary, padding: 0 }}>
                            View Audit History <ArrowUpRight size={13} style={{ verticalAlign: 'middle' }} />
                        </Button>
                    </Space>
                </div>

                <Table
                    columns={columns}
                    dataSource={alerts}
                    size="small"
                    pagination={{ pageSize: 5 }}
                    scroll={{ x: 'max-content' }}
                    onRow={(record) => ({
                        onClick: () => handleViewAlert(record),
                        style: { cursor: 'pointer' },
                    })}
                />
            </div>

            {/* Drawer */}
            <Drawer
                title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <SlidersHorizontal size={16} color={THEME.textPrimary} />
                        <span style={{ fontWeight: 600 }}>Alert Triage & Action</span>
                    </div>
                }
                width={420}
                onClose={() => setIsDrawerOpen(false)}
                open={isDrawerOpen}
            >
                {selectedAlert && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                        <div style={{ background: '#F8FAFC', padding: 14, borderRadius: 8, border: `1px solid ${THEME.border}` }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                                <MonoText style={{ fontWeight: 600, color: THEME.textPrimary }}>{selectedAlert.key}</MonoText>
                                <Tag color={selectedAlert.severity === 'HIGH' ? 'error' : 'warning'}>{selectedAlert.status}</Tag>
                            </div>
                            <div style={{ fontSize: 14, fontWeight: 600, color: THEME.textPrimary, marginBottom: 4 }}>
                                {selectedAlert.ruleName}
                            </div>
                            <div style={{ fontSize: 12, color: THEME.textSecondary }}>{selectedAlert.description}</div>
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                                <span style={{ color: THEME.textSecondary }}>Severity Level:</span>
                                <span style={{ fontWeight: 600, color: selectedAlert.severity === 'HIGH' ? THEME.high.main : THEME.medium.main }}>{selectedAlert.severity}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                                <span style={{ color: THEME.textSecondary }}>Account ID:</span>
                                <MonoText>{selectedAlert.accountId}</MonoText>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                                <span style={{ color: THEME.textSecondary }}>Trigger Amount:</span>
                                <MonoText style={{ color: THEME.textPrimary, fontWeight: 600 }}>${selectedAlert.amount.toLocaleString()}</MonoText>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                                <span style={{ color: THEME.textSecondary }}>Event Time:</span>
                                <MonoText style={{ color: THEME.textMuted }}>{selectedAlert.timestamp}</MonoText>
                            </div>
                        </div>

                        <div style={{ borderTop: `1px solid ${THEME.border}`, paddingTop: 14 }}>
                            <div style={{ fontSize: 12, fontWeight: 600, color: THEME.textPrimary, marginBottom: 10 }}>
                                Update Alert Status Workflow
                            </div>
                            <Space direction="vertical" style={{ width: '100%' }}>
                                {selectedAlert.status === 'OPEN' && (
                                    <Button block type="primary" style={{ background: THEME.textPrimary }} onClick={() => handleUpdateStatus('ACKNOWLEDGED')}>
                                        Acknowledge Alert
                                    </Button>
                                )}
                                {(selectedAlert.status === 'OPEN' || selectedAlert.status === 'ACKNOWLEDGED') && (
                                    <Button block style={{ borderColor: THEME.info.main, color: THEME.info.main }} onClick={() => handleUpdateStatus('INVESTIGATING')}>
                                        Mark as Investigating
                                    </Button>
                                )}
                                <Button block type="dashed" danger onClick={() => handleUpdateStatus('DISMISSED')}>
                                    Dismiss as False Positive
                                </Button>
                                <Button block style={{ backgroundColor: THEME.success.main, color: '#fff' }} onClick={() => handleUpdateStatus('CLOSED')}>
                                    Close Alert (Resolved)
                                </Button>
                            </Space>
                        </div>
                    </div>
                )}
            </Drawer>
        </div>
    );
}