import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
    Table,
    Tag,
    Input,
    Button,
    Switch,
    Drawer,
    Form,
    Select,
    InputNumber,
    message,
    Descriptions,
} from 'antd';
import { Search, Plus, SlidersHorizontal, RefreshCw } from 'lucide-react';
import axios from 'axios';
import { COLORS } from '../constants/theme';

const API_BASE = '/api/rules';

const fieldLabelStyle = {
    display: 'block',
    fontSize: 12,
    fontWeight: 600,
    color: COLORS.ink,
    marginBottom: 6,
};

const filterInputStyle = {
    width: '100%',
};

const monoCell = (content, extraStyle = {}) => (
    <span style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: 13, color: COLORS.slate, ...extraStyle }}>
    {content}
  </span>
);

const SEVERITY_COLORS = {
    HIGH: 'error',
    MEDIUM: 'warning',
    LOW: 'default',
};

function formatDateTime(value) {
    if (!value) return '—';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export default function MonitoringRulesPage() {
    // 顶部筛选条件拆分
    const [filters, setFilters] = useState({
        type: undefined,
        enabled: undefined,
        severity: undefined,
        keyword: '',
    });

    const [rules, setRules] = useState([]);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

    // Drawer / Form 状态
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [editingRule, setEditingRule] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [form] = Form.useForm();

    // 获取规则列表 (对应 RulePageResponse)
    const fetchRules = useCallback(async () => {
        setLoading(true);
        try {
            const params = {
                page: pagination.current - 1, // 后端分页 index 从 0 开始
                size: pagination.pageSize,
            };

            if (filters.type) params.type = filters.type;
            if (filters.enabled !== undefined && filters.enabled !== null) params.enabled = filters.enabled;
            if (filters.severity) params.severity = filters.severity;

            const res = await axios.get(API_BASE, { params });
            const data = res.data;
            const list = data.content || data.items || data.rules || [];
            const totalCount = data.totalElements ?? data.total ?? list.length;

            setRules(list);
            setTotal(totalCount);
        } catch (error) {
            console.error('Fetch rules failed:', error);
            message.error(error.response?.data?.message || '获取规则列表失败');
        } finally {
            setLoading(false);
        }
    }, [pagination.current, pagination.pageSize, filters.type, filters.enabled, filters.severity]);

    useEffect(() => {
        fetchRules();
    }, [fetchRules]);

    // 前端关键字过滤
    const filteredData = useMemo(() => {
        const q = filters.keyword.trim().toLowerCase();
        if (!q) return rules;
        return rules.filter(
            (row) =>
                row.name?.toLowerCase().includes(q) ||
                row.description?.toLowerCase().includes(q)
        );
    }, [rules, filters.keyword]);

    // 修改启用状态 (PATCH /api/rules/{ruleId}/enabled)
    const handleToggleRule = async (record, enabled) => {
        try {
            const res = await axios.patch(`${API_BASE}/${record.id}/enabled`, {
                enabled,
                version: record.version, // 带上乐观锁版本号
            });
            message.success(`规则已${enabled ? '启用' : '停用'}`);
            setRules((prev) =>
                prev.map((r) => (r.id === record.id ? { ...r, enabled: res.data.enabled, version: res.data.version } : r))
            );
        } catch (error) {
            console.error('Toggle status failed:', error);
            if (error.response?.status === 409) {
                message.error('规则已被他人修改，请刷新重试');
                fetchRules();
            } else {
                message.error(error.response?.data?.message || '更新规则状态失败');
            }
        }
    };

    // 打开新建 Drawer
    const handleAddNew = () => {
        setEditingRule(null);
        form.resetFields();
        form.setFieldsValue({
            name: '',
            description: '',
            type: 'AMOUNT_THRESHOLD',
            severity: 'MEDIUM',
            enabled: true,
            currency: 'USD',
            thresholdAmount: 10000,
            transactionCount: 5,
            timeWindowMinutes: 10,
            dailyLimitAmount: 50000,
        });
        setIsDrawerOpen(true);
    };

    // 打开编辑 Drawer
    const handleEdit = (record) => {
        setEditingRule(record);
        form.resetFields();
        form.setFieldsValue({
            name: record.name,
            description: record.description,
            type: record.type,
            severity: record.severity,
            currency: record.currency || 'USD',
            thresholdAmount: record.thresholdAmount,
            transactionCount: record.transactionCount,
            timeWindowMinutes: record.timeWindowMinutes,
            dailyLimitAmount: record.dailyLimitAmount,
        });
        setIsDrawerOpen(true);
    };

    // 保存规则
    const handleSave = async () => {
        try {
            const values = await form.validateFields();
            setSubmitting(true);

            if (editingRule) {
                // 修改规则 (PUT /api/rules/{ruleId})
                const payload = {
                    name: values.name,
                    description: values.description,
                    severity: values.severity,
                    version: editingRule.version, // 乐观锁
                };

                if (editingRule.type === 'AMOUNT_THRESHOLD') {
                    payload.currency = values.currency;
                    payload.thresholdAmount = values.thresholdAmount;
                } else if (editingRule.type === 'VELOCITY') {
                    payload.transactionCount = values.transactionCount;
                    payload.timeWindowMinutes = values.timeWindowMinutes;
                } else if (editingRule.type === 'DAILY_LIMIT') {
                    payload.currency = values.currency;
                    payload.dailyLimitAmount = values.dailyLimitAmount;
                }

                await axios.put(`${API_BASE}/${editingRule.id}`, payload);
                message.success('规则修改成功');
            } else {
                // 新增规则 (POST /api/rules)
                const payload = {
                    name: values.name,
                    description: values.description,
                    type: values.type,
                    severity: values.severity,
                    enabled: values.enabled ?? true,
                };

                if (values.type === 'AMOUNT_THRESHOLD') {
                    payload.currency = values.currency;
                    payload.thresholdAmount = values.thresholdAmount;
                } else if (values.type === 'VELOCITY') {
                    payload.transactionCount = values.transactionCount;
                    payload.timeWindowMinutes = values.timeWindowMinutes;
                } else if (values.type === 'DAILY_LIMIT') {
                    payload.currency = values.currency;
                    payload.dailyLimitAmount = values.dailyLimitAmount;
                }

                await axios.post(API_BASE, payload);
                message.success('规则创建成功');
            }

            setIsDrawerOpen(false);
            fetchRules();
        } catch (error) {
            console.error('Save rule failed:', error);
            if (error.response?.status === 409) {
                message.error('提交失败：规则已被他人修改，请重新加载');
            } else {
                message.error(error.response?.data?.message || '保存失败，请检查输入项');
            }
        } finally {
            setSubmitting(false);
        }
    };

    const columns = [
        {
            title: 'Rule ID',
            dataIndex: 'id',
            key: 'id',
            width: 90,
            render: (v) => monoCell(v),
        },
        {
            title: 'Rule Name',
            dataIndex: 'name',
            key: 'name',
            render: (v, record) => (
                <div>
                    <div style={{ fontSize: 13, fontWeight: 500, color: COLORS.ink }}>{v}</div>
                    {record.description && (
                        <div style={{ fontSize: 12, color: COLORS.slate, marginTop: 2 }}>{record.description}</div>
                    )}
                </div>
            ),
        },
        {
            title: 'Type',
            dataIndex: 'type',
            key: 'type',
            render: (v) => monoCell(v),
        },
        {
            title: 'Severity',
            dataIndex: 'severity',
            key: 'severity',
            render: (severity) => <Tag color={SEVERITY_COLORS[severity]}>{severity}</Tag>,
        },
        {
            title: 'Enabled',
            dataIndex: 'enabled',
            key: 'enabled',
            align: 'center',
            render: (enabled, record) => (
                <Switch
                    size="small"
                    checked={enabled}
                    onChange={(checked, e) => {
                        e.stopPropagation();
                        handleToggleRule(record, checked);
                    }}
                />
            ),
        },
        {
            title: 'Action',
            key: 'action',
            align: 'right',
            render: (_, record) => (
                <Button
                    type="link"
                    size="small"
                    onClick={(e) => {
                        e.stopPropagation();
                        handleEdit(record);
                    }}
                >
                    Edit
                </Button>
            ),
        },
    ];

    const currentType = Form.useWatch('type', form);

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {/* 卡片过滤器 */}
            <div
                style={{
                    background: COLORS.card,
                    border: `1px solid ${COLORS.border}`,
                    borderRadius: 12,
                    padding: '16px 20px',
                }}
            >
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                        gap: 12,
                        alignItems: 'end',
                    }}
                >
                    <div>
                        <label style={fieldLabelStyle}>Search Keyword</label>
                        <Input
                            allowClear
                            placeholder="Search Name / Description"
                            prefix={<Search size={15} color={COLORS.slate} />}
                            style={filterInputStyle}
                            value={filters.keyword}
                            onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
                        />
                    </div>

                    <div>
                        <label style={fieldLabelStyle}>Rule Type</label>
                        <Select
                            allowClear
                            placeholder="All Types"
                            style={filterInputStyle}
                            value={filters.type}
                            onChange={(val) => {
                                setFilters((prev) => ({ ...prev, type: val }));
                                setPagination((p) => ({ ...p, current: 1 }));
                            }}
                        >
                            <Select.Option value="AMOUNT_THRESHOLD">AMOUNT_THRESHOLD</Select.Option>
                            <Select.Option value="VELOCITY">VELOCITY</Select.Option>
                            <Select.Option value="NEW_PAYEE">NEW_PAYEE</Select.Option>
                            <Select.Option value="DAILY_LIMIT">DAILY_LIMIT</Select.Option>
                        </Select>
                    </div>

                    <div>
                        <label style={fieldLabelStyle}>Enabled Status</label>
                        <Select
                            allowClear
                            placeholder="All Status"
                            style={filterInputStyle}
                            value={filters.enabled}
                            onChange={(val) => {
                                setFilters((prev) => ({ ...prev, enabled: val }));
                                setPagination((p) => ({ ...p, current: 1 }));
                            }}
                        >
                            <Select.Option value={true}>Enabled</Select.Option>
                            <Select.Option value={false}>Disabled</Select.Option>
                        </Select>
                    </div>

                    <div>
                        <label style={fieldLabelStyle}>Severity</label>
                        <Select
                            allowClear
                            placeholder="All Severities"
                            style={filterInputStyle}
                            value={filters.severity}
                            onChange={(val) => {
                                setFilters((prev) => ({ ...prev, severity: val }));
                                setPagination((p) => ({ ...p, current: 1 }));
                            }}
                        >
                            <Select.Option value="HIGH">HIGH</Select.Option>
                            <Select.Option value="MEDIUM">MEDIUM</Select.Option>
                            <Select.Option value="LOW">LOW</Select.Option>
                        </Select>
                    </div>

                    <div style={{ display: 'flex', gap: 8 }}>
                        <Button
                            icon={<RefreshCw size={14} />}
                            onClick={fetchRules}
                            loading={loading}
                            style={{ flex: 1 }}
                        >
                            Reload
                        </Button>
                        <Button type="primary" icon={<Plus size={15} />} onClick={handleAddNew}>
                            New Rule
                        </Button>
                    </div>
                </div>
            </div>

            {/* 数据表格 */}
            <div style={{ background: COLORS.card, border: `1px solid ${COLORS.border}`, borderRadius: 12, overflow: 'hidden' }}>
                <Table
                    rowKey="id"
                    loading={loading}
                    columns={columns}
                    dataSource={filteredData}
                    size="middle"
                    pagination={{
                        current: pagination.current,
                        pageSize: pagination.pageSize,
                        total: total,
                        showSizeChanger: true,
                        showQuickJumper: true, // 👈 加上这行，右下角就会多出一个“Go to [  ]”的输入框
                        onChange: (page, pageSize) => setPagination({ current: page, pageSize }),
                        showTotal: (t) => `Total ${t} rules`,
                    }}
                    onRow={(record) => ({
                        onClick: () => handleEdit(record),
                        style: { cursor: 'pointer' },
                    })}
                />
            </div>

            {/* 新增 / 编辑抽屉 */}
            <Drawer
                title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <SlidersHorizontal size={16} color={COLORS.primary || '#1677ff'} />
                        <span>{editingRule ? `Edit Rule #${editingRule.id}` : 'Create New Rule'}</span>
                    </div>
                }
                width={440}
                onClose={() => setIsDrawerOpen(false)}
                open={isDrawerOpen}
                extra={
                    <Button type="primary" onClick={handleSave} loading={submitting}>
                        Save
                    </Button>
                }
            >
                <Form form={form} layout="vertical">
                    {editingRule && (
                        <Descriptions column={2} size="small" style={{ marginBottom: 16 }} bordered>
                            <Descriptions.Item label="Version">{editingRule.version}</Descriptions.Item>
                            <Descriptions.Item label="Created">{formatDateTime(editingRule.createdAt)}</Descriptions.Item>
                        </Descriptions>
                    )}

                    <Form.Item name="name" label="Rule Name" rules={[{ required: true, message: 'Please enter rule name' }]}>
                        <Input placeholder="e.g., Large USD Transaction" />
                    </Form.Item>

                    <Form.Item name="description" label="Description">
                        <Input.TextArea rows={2} placeholder="Describe the trigger condition" />
                    </Form.Item>

                    <Form.Item name="type" label="Rule Type" rules={[{ required: true }]}>
                        <Select disabled={!!editingRule}>
                            <Select.Option value="AMOUNT_THRESHOLD">AMOUNT_THRESHOLD</Select.Option>
                            <Select.Option value="VELOCITY">VELOCITY</Select.Option>
                            <Select.Option value="NEW_PAYEE">NEW_PAYEE</Select.Option>
                            <Select.Option value="DAILY_LIMIT">DAILY_LIMIT</Select.Option>
                        </Select>
                    </Form.Item>

                    <Form.Item name="severity" label="Severity" rules={[{ required: true }]}>
                        <Select>
                            <Select.Option value="HIGH">HIGH</Select.Option>
                            <Select.Option value="MEDIUM">MEDIUM</Select.Option>
                            <Select.Option value="LOW">LOW</Select.Option>
                        </Select>
                    </Form.Item>

                    {/* 抽屉编辑框内部仍保留针对不同 Rule Type 的参数设置项（与创建/更新 API RequestBody 参数对齐） */}
                    {(currentType === 'AMOUNT_THRESHOLD' || currentType === 'DAILY_LIMIT') && (
                        <Form.Item name="currency" label="Currency" rules={[{ required: true }]}>
                            <Select>
                                <Select.Option value="USD">USD</Select.Option>
                                <Select.Option value="EUR">EUR</Select.Option>
                                <Select.Option value="CNY">CNY</Select.Option>
                            </Select>
                        </Form.Item>
                    )}

                    {currentType === 'AMOUNT_THRESHOLD' && (
                        <Form.Item name="thresholdAmount" label="Threshold Amount" rules={[{ required: true, message: 'Please enter threshold amount' }]}>
                            <InputNumber style={{ width: '100%' }} min={0} precision={2} />
                        </Form.Item>
                    )}

                    {currentType === 'VELOCITY' && (
                        <div style={{ display: 'flex', gap: 12 }}>
                            <Form.Item name="transactionCount" label="Transaction Count" style={{ flex: 1 }} rules={[{ required: true }]}>
                                <InputNumber style={{ width: '100%' }} min={1} precision={0} />
                            </Form.Item>
                            <Form.Item name="timeWindowMinutes" label="Time Window (Mins)" style={{ flex: 1 }} rules={[{ required: true }]}>
                                <InputNumber style={{ width: '100%' }} min={1} precision={0} />
                            </Form.Item>
                        </div>
                    )}

                    {currentType === 'DAILY_LIMIT' && (
                        <Form.Item name="dailyLimitAmount" label="Daily Limit Amount" rules={[{ required: true, message: 'Please enter daily limit' }]}>
                            <InputNumber style={{ width: '100%' }} min={0} precision={2} />
                        </Form.Item>
                    )}

                    {!editingRule && (
                        <Form.Item name="enabled" label="Initial Status" valuePropName="checked">
                            <Switch checkedChildren="Enabled" unCheckedChildren="Disabled" />
                        </Form.Item>
                    )}
                </Form>
            </Drawer>
        </div>
    );
}