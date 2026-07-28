import React, { useState, useMemo } from 'react';
import { Table, Tag, Input, Button, Switch, Drawer, Form, Select, InputNumber } from 'antd';
import { Search, Plus, Trash2, SlidersHorizontal } from 'lucide-react';
import { COLORS } from '../constants/theme';

// 与模板一致的 Mono 字体渲染辅助函数
const monoCell = (content, extraStyle = {}) => (
    <span style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: 13, color: COLORS.slate, ...extraStyle }}>
    {content}
  </span>
);

const INITIAL_RULES = [
    {
        key: 'rule-1',
        name: 'High Value Transaction',
        type: 'AMOUNT_THRESHOLD',
        severity: 'HIGH',
        parameters: { threshold: 10000 },
        enabled: true,
    },
    {
        key: 'rule-2',
        name: 'Rapid Transactions',
        type: 'VELOCITY',
        severity: 'HIGH',
        parameters: { maxTransactions: 5, timeWindowValue: 10, timeWindowUnit: 'Minutes' },
        enabled: true,
    },
    {
        key: 'rule-3',
        name: 'New Payee Detection',
        type: 'NEW_PAYEE',
        severity: 'MEDIUM',
        parameters: {},
        enabled: true,
    },
    {
        key: 'rule-4',
        name: 'Daily Limit Exceeded',
        type: 'DAILY_LIMIT',
        severity: 'HIGH',
        parameters: { dailyLimit: 50000 },
        enabled: false,
    },
];

const SEVERITY_COLORS = {
    HIGH: 'error',
    MEDIUM: 'warning',
    LOW: 'default',
};

export default function MonitoringRulesPage() {
    // 保存变化的数据并渲染页面
    const [rules, setRules] = useState(INITIAL_RULES);
    const [keyword, setKeyword] = useState('');
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [editingRule, setEditingRule] = useState(null);
    const [form] = Form.useForm();

    // 模糊搜索过滤  "搜索匹配条件的规则"
    const filteredData = useMemo(() => {
        const q = keyword.trim().toLowerCase();
        if (!q) return rules;
        return rules.filter(
            (row) =>
                row.name.toLowerCase().includes(q) ||
                row.type.toLowerCase().includes(q) ||
                row.severity.toLowerCase().includes(q)
        );
    }, [rules, keyword]);

    // 格式化参数显示，转换成用户能看懂的文字，显示在页面表格里
    const formatParameters = (rule) => {
        switch (rule.type) {
            case 'AMOUNT_THRESHOLD':
                return `Threshold: $${Number(rule.parameters?.threshold || 0).toLocaleString('en-US')}`;
            case 'VELOCITY':
                return `Max ${rule.parameters?.maxTransactions || 0} txs in ${rule.parameters?.timeWindowValue || 0} ${rule.parameters?.timeWindowUnit?.toLowerCase() || 'mins'}`;
            case 'DAILY_LIMIT':
                return `Daily Limit: $${Number(rule.parameters?.dailyLimit || 0).toLocaleString('en-US')}`;
            case 'NEW_PAYEE':
            default:
                return 'No parameter required';
        }
    };

    // 快捷切换规则开启/关闭
    const handleToggleRule = (key, enabled) => {
        setRules((prev) => prev.map((r) => (r.key === key ? { ...r, enabled } : r)));
    };

    // 删除规则
    const handleDeleteRule = (key, e) => {
        e.stopPropagation();
        setRules((prev) => prev.filter((r) => r.key !== key));
    };

    // 打开新建抽屉
    const handleAddNew = () => {
        setEditingRule(null);  // 清空当前编辑对象，创建新规则
        form.resetFields();
        form.setFieldsValue({
            name: 'New Rule',
            type: 'AMOUNT_THRESHOLD',
            severity: 'MEDIUM',
            enabled: true,
            parameters: { threshold: 1000 },
        });
        setIsDrawerOpen(true); // 打开抽屉
    };

    // 打开编辑抽屉
    const handleEdit = (record) => {
        setEditingRule(record);
        form.setFieldsValue(JSON.parse(JSON.stringify(record)));
        setIsDrawerOpen(true);
    };

    // 保存规则
    const handleSave = async () => {
        try {
            const values = await form.validateFields(); // 把用户填写的数据拿出来
            if (editingRule) { // 有没有正在编辑的规则
                setRules((prev) => prev.map((r) => (r.key === editingRule.key ? { ...r, ...values } : r))); // 编辑旧规则
            } else {  // 新增新规则
                const newRule = {
                    ...values,
                    key: `rule-${Date.now()}`,
                };
                setRules((prev) => [...prev, newRule]);  // 添加到规则数组
            }
            setIsDrawerOpen(false);  // 关闭右侧窗口
        } catch (error) {
            console.error('Validation failed:', error);
        }
    };

    const columns = [  // 表格列配置
        {
            title: 'Rule Name',
            dataIndex: 'name',
            key: 'name',
            render: (v) => <span style={{ fontSize: 13, fontWeight: 500, color: COLORS.ink }}>{v}</span>,
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
            title: 'Parameters',
            key: 'parameters',
            render: (_, record) => (
                <span style={{ fontSize: 13, color: COLORS.slate }}>{formatParameters(record)}</span>
            ),
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
                        handleToggleRule(record.key, checked);
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
                    type="text"
                    danger
                    icon={<Trash2 size={15} />}
                    onClick={(e) => handleDeleteRule(record.key, e)}
                />
            ),
        },
    ];

    const currentType = Form.useWatch('type', form); // 实时监听表单里面 type 这个字段的变化

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {/* 搜索与新增工具栏 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Input
                    allowClear
                    placeholder="search Rule Name / Type / Severity"
                    prefix={<Search size={15} color={COLORS.slate} />}
                    style={{ maxWidth: 380 }}
                    value={keyword}
                    onChange={(e) => setKeyword(e.target.value)}
                />
                <Button type="primary" icon={<Plus size={15} />} onClick={handleAddNew}>
                    Add New Rule
                </Button>
            </div>

            {/* 表格主容器，样式对齐模板 */}
            <div style={{ background: COLORS.card, border: `1px solid ${COLORS.border}`, borderRadius: 12, overflow: 'hidden' }}>
                <Table
                    columns={columns}
                    dataSource={filteredData}
                    size="middle"
                    pagination={{ pageSize: 6, showTotal: (total) => `${total} rules` }}
                    onRow={(record) => ({
                        onClick: () => handleEdit(record),
                        style: { cursor: 'pointer' },
                    })}
                />
            </div>

            {/* 编辑/新建抽屉/右侧弹窗 */}
            <Drawer
                title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <SlidersHorizontal size={16} color={COLORS.primary || '#1677ff'} />
                        <span>{editingRule ? 'Edit Rule' : 'Create New Rule'}</span>
                    </div>
                }
                width={380}
                onClose={() => setIsDrawerOpen(false)}
                open={isDrawerOpen}
                extra={
                    <Button type="primary" onClick={handleSave}>
                        Save
                    </Button>
                }
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="name" label="Rule Name" rules={[{ required: true, message: 'Please enter rule name' }]}>
                        <Input />
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

                    {/* 参数根据类型动态渲染 */}
                    {currentType === 'AMOUNT_THRESHOLD' && (
                        <Form.Item name={['parameters', 'threshold']} label="Threshold Amount ($)" rules={[{ required: true }]}>
                            <InputNumber style={{ width: '100%' }} />
                        </Form.Item>
                    )}

                    {currentType === 'VELOCITY' && (
                        <>
                            <Form.Item name={['parameters', 'maxTransactions']} label="Max Transactions" rules={[{ required: true }]}>
                                <InputNumber style={{ width: '100%' }} />
                            </Form.Item>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <Form.Item name={['parameters', 'timeWindowValue']} label="Window Value" style={{ flex: 1 }} rules={[{ required: true }]}>
                                    <InputNumber style={{ width: '100%' }} />
                                </Form.Item>
                                <Form.Item name={['parameters', 'timeWindowUnit']} label="Unit" style={{ flex: 1 }} rules={[{ required: true }]}>
                                    <Select>
                                        <Select.Option value="Seconds">Seconds</Select.Option>
                                        <Select.Option value="Minutes">Minutes</Select.Option>
                                        <Select.Option value="Hours">Hours</Select.Option>
                                    </Select>
                                </Form.Item>
                            </div>
                        </>
                    )}

                    {currentType === 'DAILY_LIMIT' && (
                        <Form.Item name={['parameters', 'dailyLimit']} label="Daily Limit ($)" rules={[{ required: true }]}>
                            <InputNumber style={{ width: '100%' }} />
                        </Form.Item>
                    )}

                    <Form.Item name="enabled" label="Status" valuePropName="checked">
                        <Switch checkedChildren="Enabled" unCheckedChildren="Disabled" />
                    </Form.Item>
                </Form>
            </Drawer>
        </div>
    );
}