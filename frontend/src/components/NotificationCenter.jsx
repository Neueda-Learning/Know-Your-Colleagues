import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Badge,
  Button,
  Empty,
  Popover,
  Space,
  Tag,
  Tooltip,
  notification,
} from "antd";
import {
  Bell,
  CheckCheck,
  Trash2,
  Wifi,
  WifiOff,
} from "lucide-react";
import { COLORS } from "../constants/theme";

const STORAGE_KEY = "transaction-monitoring.notifications";
const MAX_NOTIFICATIONS = 50;
const REALTIME_EVENT_NAME = "kyc:realtime-notification";

function loadStoredNotifications() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
    return Array.isArray(parsed) ? parsed.slice(0, MAX_NOTIFICATIONS) : [];
  } catch {
    return [];
  }
}

function saveNotifications(items) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
  } catch {
    // Notifications still work in memory when storage is unavailable.
  }
}

function resolveWebSocketUrl() {
  if (import.meta.env.VITE_NOTIFICATION_WS_URL) {
    return import.meta.env.VITE_NOTIFICATION_WS_URL;
  }
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/notifications`;
}

function formatTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat(undefined, {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

const LEVEL_COLOR = {
  SUCCESS: "success",
  INFO: "processing",
  WARNING: "warning",
  CRITICAL: "error",
};

export default function NotificationCenter({ onNavigate }) {
  const [notificationApi, contextHolder] = notification.useNotification();
  const [items, setItems] = useState(loadStoredNotifications);
  const [popoverOpen, setPopoverOpen] = useState(false);
  const [connectionState, setConnectionState] = useState("CONNECTING");
  const navigateRef = useRef(onNavigate);

  useEffect(() => {
    navigateRef.current = onNavigate;
  }, [onNavigate]);

  const replaceItems = useCallback((updater) => {
    setItems((previous) => {
      const next = updater(previous);
      saveNotifications(next);
      return next;
    });
  }, []);

  const openTarget = useCallback((item) => {
    replaceItems((previous) =>
      previous.map((candidate) =>
        candidate.id === item.id ? { ...candidate, read: true } : candidate
      )
    );
    setPopoverOpen(false);
    if (item.action) navigateRef.current?.(item.action);
  }, [replaceItems]);

  const showToast = useCallback((item) => {
    const config = {
      key: item.id,
      message: item.title,
      description: item.message,
      duration: item.level === "CRITICAL" ? 0 : item.level === "WARNING" ? 8 : 4.5,
      placement: "topRight",
      onClick: () => openTarget(item),
      style: { cursor: item.action ? "pointer" : "default" },
    };
    if (item.level === "CRITICAL") notificationApi.error(config);
    else if (item.level === "WARNING") notificationApi.warning(config);
    else if (item.level === "SUCCESS") notificationApi.success(config);
    else notificationApi.info(config);
  }, [notificationApi, openTarget]);

  const handleIncoming = useCallback((payload) => {
    if (!payload?.id || !payload?.type || !payload?.occurredAt) return;
    const item = { ...payload, read: false };
    replaceItems((previous) => [
      item,
      ...previous.filter((candidate) => candidate.id !== item.id),
    ].slice(0, MAX_NOTIFICATIONS));
    window.dispatchEvent(new CustomEvent(REALTIME_EVENT_NAME, { detail: item }));
    showToast(item);
  }, [replaceItems, showToast]);

  useEffect(() => {
    let socket;
    let reconnectTimer;
    let stopped = false;

    const connect = () => {
      if (stopped) return;
      setConnectionState("CONNECTING");
      socket = new WebSocket(resolveWebSocketUrl());
      socket.onopen = () => setConnectionState("CONNECTED");
      socket.onmessage = (event) => {
        try {
          handleIncoming(JSON.parse(event.data));
        } catch {
          // Ignore malformed messages and keep the live connection available.
        }
      };
      socket.onerror = () => setConnectionState("DISCONNECTED");
      socket.onclose = () => {
        setConnectionState("DISCONNECTED");
        if (!stopped) reconnectTimer = window.setTimeout(connect, 3_000);
      };
    };

    connect();
    return () => {
      stopped = true;
      window.clearTimeout(reconnectTimer);
      socket?.close();
    };
  }, [handleIncoming]);

  const unreadCount = useMemo(
    () => items.filter((item) => !item.read).length,
    [items]
  );

  const markAllRead = () => {
    replaceItems((previous) => previous.map((item) => ({ ...item, read: true })));
  };

  const clearAll = () => replaceItems(() => []);

  const content = (
    <div style={{ width: 360, maxWidth: "calc(100vw - 32px)" }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
        <div>
          <div style={{ fontSize: 14, fontWeight: 600, color: COLORS.ink }}>Real-time notifications</div>
          <div style={{ fontSize: 11, color: COLORS.slate }}>{unreadCount} unread</div>
        </div>
        <Space size={4}>
          <Tooltip title="Mark all as read">
            <Button type="text" size="small" icon={<CheckCheck size={15} />} onClick={markAllRead} />
          </Tooltip>
          <Tooltip title="Clear notifications">
            <Button type="text" size="small" icon={<Trash2 size={15} />} onClick={clearAll} />
          </Tooltip>
        </Space>
      </div>

      <div style={{ maxHeight: 420, overflowY: "auto", margin: "0 -8px" }}>
        {items.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No real-time notifications" />
        ) : items.map((item) => (
          <button
            key={item.id}
            type="button"
            onClick={() => openTarget(item)}
            style={{
              width: "100%",
              border: "none",
              borderTop: `1px solid ${COLORS.border}`,
              background: item.read ? "#fff" : COLORS.accentSoft,
              padding: "11px 12px",
              textAlign: "left",
              cursor: "pointer",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: item.read ? 500 : 650, color: COLORS.ink }}>
                {item.title}
              </span>
              <Tag color={LEVEL_COLOR[item.level] || "default"} style={{ margin: 0, fontSize: 10 }}>
                {item.level}
              </Tag>
            </div>
            <div style={{ fontSize: 12, color: COLORS.slate, marginTop: 5, lineHeight: 1.45 }}>
              {item.message}
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginTop: 7, fontSize: 11 }}>
              <span style={{ color: COLORS.accent }}>{item.action?.label || "View details"}</span>
              <span style={{ color: COLORS.slate }}>{formatTime(item.occurredAt)}</span>
            </div>
          </button>
        ))}
      </div>
    </div>
  );

  const connected = connectionState === "CONNECTED";

  return (
    <>
      {contextHolder}
      <Space size={10}>
        <Tooltip title={connected ? "Real-time notifications connected" : "Reconnecting real-time notifications"}>
          <span style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 11, color: connected ? COLORS.green : COLORS.slate }}>
            {connected ? <Wifi size={14} /> : <WifiOff size={14} />}
            {connected ? "Live" : "Offline"}
          </span>
        </Tooltip>
        <Popover
          trigger="click"
          placement="bottomRight"
          open={popoverOpen}
          onOpenChange={setPopoverOpen}
          content={content}
        >
          <Badge count={unreadCount} size="small" overflowCount={99}>
            <Button aria-label="Open notification center" icon={<Bell size={17} />} />
          </Badge>
        </Popover>
      </Space>
    </>
  );
}

export { REALTIME_EVENT_NAME };
