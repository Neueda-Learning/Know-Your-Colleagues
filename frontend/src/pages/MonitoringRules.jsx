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
    Modal,
} from 'antd';
import { Search, Plus, SlidersHorizontal, RefreshCw, Trash2 } from 'lucide-react';
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

// 修改LOW标签为蓝色processing
const SEVERITY_COLORS = {
    HIGH: 'error',
    MEDIUM: 'warning',
    LOW: 'processing',
};

function formatDateTime(value) {
    if (!value) return '—';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export default function MonitoringRulesPage() {
    // 顶部筛选条件拆分（对齐后端GET参数：keyword, type, enabled, severity）
    const [filters, setFilters] = useState({
        keyword: '', // 模糊：规则名称/描述/ID（后端识别数字精确匹配ID）
        type: undefined, // 精确枚举
        enabled: undefined, // 精确布尔
        severity: undefined, // 精确枚举
    });
    // 保存点击搜索前的筛选快照，用于接口请求
    const [submitFilters, setSubmitFilters] = useState({ ...filters });

    const [rules, setRules] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchLoading, setSearchLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 10 });

    // Drawer / Form 状态
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [editingRule, setEditingRule] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const [form] = Form.useForm();

    // 删除确认弹窗状态
    const [deleteModalVisible, setDeleteModalVisible] = useState(false);
    const [targetDeleteRule, setTargetDeleteRule] = useState(null);
    const [deleteLoading, setDeleteLoading] = useState(false);

    // 获取规则列表 (完全对齐后端RuleController.getRules参数)
    const fetchRules = useCallback(async () => {
        setLoading(true);
        try {
            // 后端分页page从0开始，前端current从1开始
            const params = {
                page: pagination.current - 1,
                size: pagination.pageSize,
                keyword: submitFilters.keyword || undefined,
                type: submitFilters.type,
                enabled: submitFilters.enabled,
                severity: submitFilters.severity,
            };

            const res = await axios.get(API_BASE, { params });
            const data = res.data;
            const list = data.content || data.items || data.rules || [];
            const totalCount = data.totalElements ?? data.total ?? list.length;

            setRules(list);
            setTotal(totalCount);
        } catch (error) {
            console.error('Fetch rules failed:', error);
            message.error(error.response?.data?.message || 'Failed to obtain the rule list');
        } finally {
            setLoading(false);
            setSearchLoading(false);
        }
    }, [pagination.current, pagination.pageSize, submitFilters]);

    // 分页切换时重新查询（使用已提交的筛选条件）
    useEffect(() => {
        fetchRules();
    }, [pagination.current, pagination.pageSize, fetchRules]);

    // 点击蓝色搜索按钮触发查询
    const handleSearch = () => {
        setSearchLoading(true);
        setSubmitFilters({ ...filters });
        setPagination(p => ({ ...p, current: 1 }));
    };

    // 重置所有筛选条件
    const resetFilters = () => {
        const emptyFilter = {
            keyword: '',
            type: undefined,
            enabled: undefined,
            severity: undefined,
        };
        setFilters(emptyFilter);
        setSubmitFilters(emptyFilter);
        setPagination(p => ({ ...p, current: 1 }));
    };

    // 修改启用状态 (PATCH /api/rules/{ruleId}/enabled)
    const handleToggleRule = async (record, enabled) => {
        try {
            const res = await axios.patch(`${API_BASE}/${record.id}/enabled`, {
                enabled,
                version: record.version, // 带上乐观锁版本号
            });
            message.success(`Rule ${enabled ? 'enabled' : 'disabled'}`);
            setRules((prev) =>
                prev.map((r) => (r.id === record.id ? { ...r, enabled: res.data.enabled, version: res.data.version } : r))
            );
        } catch (error) {
            console.error('Toggle status failed:', error);
            if (error.response?.status === 409) {
                message.error('The rule has been modified by another user. Please refresh and try again.');
                fetchRules();
            } else {
                message.error(error.response?.data?.message || 'Failed to update rule status');
            }
        }
    };

    // 删除规则接口方法
    const handleDeleteRule = async () => {
        if (!targetDeleteRule) return;
        setDeleteLoading(true);
        try {
            await axios.delete(`${API_BASE}/${targetDeleteRule.id}`);
            message.success('Rule deleted successfully');
            setDeleteModalVisible(false);
            fetchRules();
        } catch (error) {
            console.error('Delete rule failed:', error);
            message.error(error.response?.data?.message || 'Failed to delete rule');
        } finally {
            setDeleteLoading(false);
            setTargetDeleteRule(null);
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

    // 打开删除确认弹窗
    const openDeleteConfirm = (record) => {
        setTargetDeleteRule(record);
        setDeleteModalVisible(true);
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
                message.success('Rule modified successfully');
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
                message.success('Rule created successfully');
            }

            setIsDrawerOpen(false);
            fetchRules();
        } catch (error) {
            console.error('Save rule failed:', error);
            if (error.response?.status === 409) {
                message.error('Submission failed: The rule has been modified by another user. Please reload and try again.');
            } else {
                message.error(error.response?.data?.message || 'Save failed, please check your input');
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
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
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
                    {/* 删除按钮仅保留垃圾桶图标，无文字 */}
                    <Button
                        danger
                        type="link"
                        size="small"
                        icon={<Trash2 size={14} />}
                        onClick={(e) => {
                            e.stopPropagation();
                            openDeleteConfirm(record);
                        }}
                    />
                </div>
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
                    {/* 修改文案：Search Keyword，提示词 input rule name */}
                    <div>
                        <label style={fieldLabelStyle}>Search Keyword</label>
                        <Input
                            allowClear
                            placeholder="Input rule name"
                            prefix={<Search size={15} color={COLORS.slate} />}
                            style={filterInputStyle}
                            value={filters.keyword}
                            onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
                        />
                    </div>

                    <div>
                        <label style={fieldLabelStyle}>Rule Type (Exact Match)</label>
                        <Select
                            allowClear
                            placeholder="All Types"
                            style={filterInputStyle}
                            value={filters.type}
                            onChange={(val) => setFilters((prev) => ({ ...prev, type: val }))}
                        >
                            <Select.Option value="AMOUNT_THRESHOLD">AMOUNT_THRESHOLD</Select.Option>
                            <Select.Option value="VELOCITY">VELOCITY</Select.Option>
                            <Select.Option value="NEW_PAYEE">NEW_PAYEE</Select.Option>
                            <Select.Option value="DAILY_LIMIT">DAILY_LIMIT</Select.Option>
                        </Select>
                    </div>

                    <div>
                        <label style={fieldLabelStyle}>Enabled Status (Exact Match)</label>
                        <Select
                            allowClear
                            placeholder="All Status"
                            style={filterInputStyle}
                            value={filters.enabled}
                            onChange={(val) => setFilters((prev) => ({ ...prev, enabled: val }))}
                        >
                            <Select.Option value={true}>Enabled</Select.Option>
                            <Select.Option value={false}>Disabled</Select.Option>
                        </Select>
                    </div>

                    <div>
                        <label style={fieldLabelStyle}>Severity (Exact Match)</label>
                        <Select
                            allowClear
                            placeholder="All Severities"
                            style={filterInputStyle}
                            value={filters.severity}
                            onChange={(val) => setFilters((prev) => ({ ...prev, severity: val }))}
                        >
                            <Select.Option value="HIGH">HIGH</Select.Option>
                            <Select.Option value="MEDIUM">MEDIUM</Select.Option>
                            <Select.Option value="LOW">LOW</Select.Option>
                        </Select>
                    </div>

                    <div style={{ display: 'flex', gap: 8 }}>
                        <Button onClick={resetFilters}>Reset</Button>
                        {/* 搜索按钮：仅放大镜图标，无文字，缩小宽度 */}
                        <Button
                            type="primary"
                            icon={<Search size={14} />}
                            onClick={handleSearch}
                            loading={searchLoading}
                        />
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
                    dataSource={rules}
                    size="middle"
                    pagination={{
                        current: pagination.current,
                        pageSize: pagination.pageSize,
                        total: total,
                        showSizeChanger: true,
                        showQuickJumper: true,
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

                    {/* 完整币种下拉，和截图保持一致 */}
                    {(currentType === 'AMOUNT_THRESHOLD' || currentType === 'DAILY_LIMIT') && (
                        <Form.Item name="currency" label="Currency" rules={[{ required: true }]}>
                            <Select placeholder="Select Currency">
                                <Select.Option value="USD">USD — US Dollar</Select.Option>
                                <Select.Option value="CNY">CNY — Chinese Yuan (RMB)</Select.Option>
                                <Select.Option value="EUR">EUR — Euro</Select.Option>
                                <Select.Option value="GBP">GBP — British Pound</Select.Option>
                                <Select.Option value="JPY">JPY — Japanese Yen</Select.Option>
                                <Select.Option value="CHF">CHF — Swiss Franc</Select.Option>
                                <Select.Option value="CAD">CAD — Canadian Dollar</Select.Option>
                                <Select.Option value="AUD">AUD — Australian Dollar</Select.Option>
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

            {/* 删除规则二次确认弹窗 */}
            <Modal
                title="Delete Rule Confirmation"
                open={deleteModalVisible}//
                confirmLoading={deleteLoading}
                onCancel={() => {
                    setDeleteModalVisible(false);
                    setTargetDeleteRule(null);
                }}
                onOk={handleDeleteRule}
                okText="Confirm Delete"
                cancelText="Cancel"
                okButtonProps={{ danger: true }}
            >
                <p>Are you sure you want to delete rule <strong>#{targetDeleteRule?.id} {targetDeleteRule?.name}</strong>?</p>
                <p style={{ color: '#f5222d' }}>This operation cannot be undone. If this rule has associated alerts, deletion will fail.</p>
            </Modal>
        </div>
    );
}