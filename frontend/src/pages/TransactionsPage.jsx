import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Table,
  Tag,
  Input,
  InputNumber,
  Button,
  DatePicker,
  Modal,
  Form,
  Select,
  Popover,
  Space,
  message,
  Drawer,
  Descriptions,
  Spin,
} from "antd";
import { Search, Calendar, ChevronDown, Plus } from "lucide-react";
import { COLORS } from "../constants/theme";
import {
  TRANSACTION_STATUS_LABEL,
  TRANSACTION_STATUS_COLOR,
  TRANSACTION_STATUS_OPTIONS,
  TRANSACTION_TYPE_LABEL,
  TRANSACTION_TYPE_COLOR,
  CURRENCY_OPTIONS,
} from "../constants/mockData";
import { fetchTransactions, fetchTransaction, createTransaction } from "../api/transactions";

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

const monoCell = (content, extraStyle = {}) => (
  <span style={{ fontFamily: "'IBM Plex Mono', monospace", fontSize: 13, color: COLORS.slate, ...extraStyle }}>
    {content}
  </span>
);

function formatDateTime(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function mapRow(tx) {
  return {
    key: tx.id ?? tx.transactionRef,
    id: tx.id,
    transactionRef: tx.transactionRef,
    accountId: tx.accountId,
    payeeId: tx.payeeId,
    transactionType: tx.transactionType,
    amount: Number(tx.amount),
    currency: tx.currency,
    transactionTime: tx.transactionTime,
    description: tx.description ?? "",
    status: tx.status,
    createdAt: tx.createdAt,
    updatedAt: tx.updatedAt,
  };
}

function formatAmount(amount, type) {
  const formatted = Number(amount).toLocaleString(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return `${type === "DEBIT" ? "-" : "+"}${formatted}`;
}

function AmountRangeInput({ minAmount, maxAmount, onChange }) {
  const [open, setOpen] = useState(false);
  const [draftMin, setDraftMin] = useState(minAmount);
  const [draftMax, setDraftMax] = useState(maxAmount);

  useEffect(() => {
    if (open) {
      setDraftMin(minAmount);
      setDraftMax(maxAmount);
    }
  }, [open, minAmount, maxAmount]);

  const display =
    minAmount != null || maxAmount != null
      ? `${minAmount ?? ""} – ${maxAmount ?? ""}`
      : undefined;

  return (
    <Popover
      trigger="click"
      open={open}
      onOpenChange={setOpen}
      placement="bottomLeft"
      content={
        <Space direction="vertical" size={10} style={{ width: 220 }}>
          <InputNumber
            style={{ width: "100%" }}
            min={0}
            precision={2}
            placeholder="Min"
            value={draftMin}
            onChange={setDraftMin}
          />
          <InputNumber
            style={{ width: "100%" }}
            min={0}
            precision={2}
            placeholder="Max"
            value={draftMax}
            onChange={setDraftMax}
          />
          <Space style={{ width: "100%", justifyContent: "flex-end" }}>
            <Button
              size="small"
              onClick={() => {
                onChange(null, null);
                setOpen(false);
              }}
            >
              Clear
            </Button>
            <Button
              type="primary"
              size="small"
              onClick={() => {
                onChange(draftMin, draftMax);
                setOpen(false);
              }}
            >
              Apply
            </Button>
          </Space>
        </Space>
      }
    >
      <Input
        readOnly
        placeholder="Min – Max"
        value={display}
        style={filterInputStyle}
        suffix={<ChevronDown size={14} color={COLORS.slate} />}
      />
    </Popover>
  );
}

export default function TransactionsPage() {
  const [filters, setFilters] = useState({
    transactionId: "",
    accountId: "",
    payeeId: "",
    minAmount: null,
    maxAmount: null,
    dateRange: null,
    status: undefined,
  });
  const [applied, setApplied] = useState({
    transactionId: "",
    accountId: "",
    payeeId: "",
    minAmount: null,
    maxAmount: null,
    dateRange: null,
    status: undefined,
  });

  const [data, setData] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [loading, setLoading] = useState(false);

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState(null);

  const handleSearch = () => {
    setApplied({ ...filters });
    setPage(0);
  };

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const idInput = applied.transactionId.trim();

      // Numeric Transaction ID → detail endpoint
      if (idInput && /^\d+$/.test(idInput)) {
        try {
          const tx = await fetchTransaction(idInput);
          const row = mapRow(tx);
          if (applied.status && row.status !== applied.status) {
            setData([]);
            setTotal(0);
          } else {
            setData([row]);
            setTotal(1);
          }
        } catch (err) {
          if (err.status === 404) {
            setData([]);
            setTotal(0);
          } else {
            throw err;
          }
        }
        return;
      }

      const params = {
        page,
        size: pageSize,
      };
      if (applied.accountId.trim()) params.accountId = applied.accountId.trim();
      if (applied.payeeId.trim()) params.payeeId = applied.payeeId.trim();
      if (applied.minAmount != null) params.minAmount = applied.minAmount;
      if (applied.maxAmount != null) params.maxAmount = applied.maxAmount;
      if (applied.status) params.status = applied.status;
      if (applied.dateRange?.[0]) {
        params.transactionTimeStart = applied.dateRange[0].startOf("day").format("YYYY-MM-DDTHH:mm:ss");
      }
      if (applied.dateRange?.[1]) {
        params.transactionTimeEnd = applied.dateRange[1].endOf("day").format("YYYY-MM-DDTHH:mm:ss");
      }

      const result = await fetchTransactions(params);
      let rows = (result.content ?? []).map(mapRow);

      // Non-numeric Transaction ID → filter by transactionRef on current page
      if (idInput) {
        const q = idInput.toLowerCase();
        rows = rows.filter(
          (r) =>
            String(r.id).includes(q) ||
            r.transactionRef?.toLowerCase().includes(q)
        );
      }

      setData(rows);
      setTotal(idInput ? rows.length : (result.totalElements ?? rows.length));
    } catch (err) {
      message.error(err.message || "Failed to load transactions");
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [applied, page, pageSize]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const columns = useMemo(
    () => [
      {
        title: "Transaction ID",
        dataIndex: "transactionRef",
        key: "transactionRef",
        fixed: "left",
        width: 220,
        render: (v) => monoCell(v, { color: COLORS.ink, fontWeight: 500 }),
      },
      {
        title: "Account ID",
        dataIndex: "accountId",
        key: "accountId",
        render: (v) => monoCell(v),
      },
      {
        title: "Payee ID",
        dataIndex: "payeeId",
        key: "payeeId",
        render: (v) => monoCell(v),
      },
      {
        title: "Type",
        dataIndex: "transactionType",
        key: "transactionType",
        filters: [
          { text: "DEBIT", value: "DEBIT" },
          { text: "CREDIT", value: "CREDIT" },
        ],
        onFilter: (value, row) => row.transactionType === value,
        render: (type) => <Tag color={TRANSACTION_TYPE_COLOR[type]}>{TRANSACTION_TYPE_LABEL[type] ?? type}</Tag>,
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
              color: row.transactionType === "DEBIT" ? COLORS.red : COLORS.green,
            }}
          >
            {row.transactionType === "DEBIT" ? "-" : "+"}
            {Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
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
        dataIndex: "transactionTime",
        key: "transactionTime",
        sorter: (a, b) => String(a.transactionTime).localeCompare(String(b.transactionTime)),
        defaultSortOrder: "descend",
        render: (v) => monoCell(formatDateTime(v)),
      },
      {
        title: "Status",
        dataIndex: "status",
        key: "status",
        render: (status) => (
          <Tag color={TRANSACTION_STATUS_COLOR[status]}>{TRANSACTION_STATUS_LABEL[status] ?? status}</Tag>
        ),
      },
    ],
    []
  );

  const openDetail = async (row) => {
    setDetailOpen(true);
    setDetail(row);
    if (!row?.id) return;
    setDetailLoading(true);
    try {
      const tx = await fetchTransaction(row.id);
      setDetail(mapRow(tx));
    } catch (err) {
      message.error(err.message || "Failed to load transaction details");
    } finally {
      setDetailLoading(false);
    }
  };

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      const amount = Number(values.amount);
      if (Number.isNaN(amount) || amount <= 0) {
        form.setFields([
          { name: "amount", errors: ["Amount must be greater than 0"] },
        ]);
        return Promise.reject();
      }
      setCreating(true);
      const body = {
        accountId: values.accountId.trim(),
        payeeId: values.payeeId.trim(),
        amount,
        currency: values.currency,
        transactionType: values.transactionType,
        description: values.description?.trim() || undefined,
        transactionTime: values.transactionTime
          ? values.transactionTime.format("YYYY-MM-DDTHH:mm:ss")
          : undefined,
      };
      await createTransaction(body);
      message.success("Transaction created");
      setCreateOpen(false);
      form.resetFields();
      if (page === 0) loadData();
      else setPage(0);
    } catch (err) {
      if (err?.errorFields) return Promise.reject();
      message.error(err.message || "Failed to create transaction");
      return Promise.reject(err);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1 style={{ fontSize: 18, fontWeight: 600, color: COLORS.ink, margin: 0 }}>
          Transactions
        </h1>
        <Button
          type="primary"
          icon={<Plus size={16} />}
          onClick={() => setCreateOpen(true)}
          style={{ background: COLORS.accent }}
        >
          Create a transaction
        </Button>
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
            gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
            gap: 12,
            alignItems: "end",
          }}
        >
          <div>
            <label style={fieldLabelStyle}>Transaction ID</label>
            <Input
              allowClear
              placeholder="Enter transaction ID"
              style={filterInputStyle}
              value={filters.transactionId}
              onChange={(e) => setFilters((prev) => ({ ...prev, transactionId: e.target.value }))}
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
            />
          </div>

          <div>
            <label style={fieldLabelStyle}>Payee ID</label>
            <Input
              allowClear
              placeholder="Enter payee ID"
              style={filterInputStyle}
              value={filters.payeeId}
              onChange={(e) => setFilters((prev) => ({ ...prev, payeeId: e.target.value }))}
            />
          </div>

          <div>
            <label style={fieldLabelStyle}>Amount Range</label>
            <AmountRangeInput
              minAmount={filters.minAmount}
              maxAmount={filters.maxAmount}
              onChange={(minAmount, maxAmount) => {
                setFilters((prev) => ({ ...prev, minAmount, maxAmount }));
              }}
            />
          </div>

          <div>
            <label style={fieldLabelStyle}>Date Range</label>
            <RangePicker
              style={{ width: "100%" }}
              placeholder={["Select range", "Select range"]}
              value={filters.dateRange}
              onChange={(range) => setFilters((prev) => ({ ...prev, dateRange: range }))}
              prefix={<Calendar size={14} color={COLORS.slate} />}
              allowClear
            />
          </div>

          <div>
            <label style={fieldLabelStyle}>Status</label>
            <Select
              allowClear
              placeholder="Select status"
              style={filterInputStyle}
              value={filters.status}
              options={TRANSACTION_STATUS_OPTIONS}
              onChange={(status) => setFilters((prev) => ({ ...prev, status }))}
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
          dataSource={data}
          loading={loading}
          size="middle"
          scroll={{ x: 1100 }}
          onRow={(record) => ({
            onClick: () => openDetail(record),
            style: { cursor: "pointer" },
          })}
          pagination={{
            current: page + 1,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: (t) => `${t} transactions`,
            onChange: (nextPage, nextSize) => {
              if (nextSize !== pageSize) {
                setPageSize(nextSize);
                setPage(0);
              } else {
                setPage(nextPage - 1);
              }
            },
          }}
        />
      </div>

      <Drawer
        title="Transaction Details"
        open={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setDetail(null);
        }}
        width={480}
      >
        <Spin spinning={detailLoading}>
          {detail && (
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Transaction Ref">
                {monoCell(detail.transactionRef, { color: COLORS.ink, fontWeight: 500 })}
              </Descriptions.Item>
              <Descriptions.Item label="Account ID">{monoCell(detail.accountId)}</Descriptions.Item>
              <Descriptions.Item label="Payee ID">{monoCell(detail.payeeId)}</Descriptions.Item>
              <Descriptions.Item label="Type">
                <Tag color={TRANSACTION_TYPE_COLOR[detail.transactionType]}>
                  {TRANSACTION_TYPE_LABEL[detail.transactionType] ?? detail.transactionType}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Amount">
                <span
                  style={{
                    fontFamily: "'IBM Plex Mono', monospace",
                    fontWeight: 500,
                    color: detail.transactionType === "DEBIT" ? COLORS.red : COLORS.green,
                  }}
                >
                  {formatAmount(detail.amount, detail.transactionType)} {detail.currency}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="Currency">{monoCell(detail.currency, { color: COLORS.ink })}</Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={TRANSACTION_STATUS_COLOR[detail.status]}>
                  {TRANSACTION_STATUS_LABEL[detail.status] ?? detail.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Transaction Time">
                {monoCell(formatDateTime(detail.transactionTime))}
              </Descriptions.Item>
              <Descriptions.Item label="Description">
                <span style={{ fontSize: 13, color: COLORS.slate, whiteSpace: "pre-wrap" }}>
                  {detail.description || "—"}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="Created At">
                {monoCell(formatDateTime(detail.createdAt))}
              </Descriptions.Item>
              <Descriptions.Item label="Updated At">
                {monoCell(formatDateTime(detail.updatedAt))}
              </Descriptions.Item>
            </Descriptions>
          )}
        </Spin>
      </Drawer>

      <Modal
        title="Create a transaction"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false);
          form.resetFields();
        }}
        onOk={handleCreate}
        confirmLoading={creating}
        okText="Create"
        destroyOnHidden
        width={520}
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 8 }}
          initialValues={{ currency: "USD", transactionType: "DEBIT" }}
        >
          <Form.Item
            name="accountId"
            label="Account ID"
            rules={[{ required: true, message: "Account ID is required" }, { max: 64 }]}
          >
            <Input placeholder="ACC-001" />
          </Form.Item>
          <Form.Item
            name="payeeId"
            label="Payee ID"
            rules={[{ required: true, message: "Payee ID is required" }, { max: 64 }]}
          >
            <Input placeholder="PAYEE-001" />
          </Form.Item>
          <Form.Item
            name="amount"
            label="Amount"
            rules={[
              { required: true, message: "Amount is required" },
              {
                validator: (_, value) => {
                  if (value === undefined || value === null || value === "") {
                    return Promise.resolve();
                  }
                  const num = Number(value);
                  if (Number.isNaN(num) || num <= 0) {
                    return Promise.reject(new Error("Amount must be greater than 0"));
                  }
                  return Promise.resolve();
                },
              },
            ]}
          >
            <InputNumber
              style={{ width: "100%" }}
              precision={2}
              placeholder="15000.00"
            />
          </Form.Item>
          <Form.Item
            name="currency"
            label="Currency"
            rules={[{ required: true, message: "Currency is required" }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Select currency"
              options={CURRENCY_OPTIONS}
            />
          </Form.Item>
          <Form.Item
            name="transactionType"
            label="Transaction Type"
            rules={[{ required: true, message: "Type is required" }]}
          >
            <Select
              options={[
                { label: "DEBIT", value: "DEBIT" },
                { label: "CREDIT", value: "CREDIT" },
              ]}
            />
          </Form.Item>
          <Form.Item name="description" label="Description" rules={[{ max: 500 }]}>
            <Input.TextArea rows={2} placeholder="Optional description" />
          </Form.Item>
          <Form.Item name="transactionTime" label="Transaction Time">
            <DatePicker showTime style={{ width: "100%" }} placeholder="Default: now (UTC)" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
