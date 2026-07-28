import { useState } from "react";
import Sidebar from "./components/Sidebar";
import NotificationCenter from "./components/NotificationCenter";
import TransactionsPage from "./pages/TransactionsPage";
import MonitoringRules from "./pages/MonitoringRules.jsx";
import AlertsPage from "./pages/AlertsPage";
import DashboardPage from "./pages/DashBoardPage";
import { COLORS, NAV_ITEMS } from "./constants/theme";
import "./App.css";

export default function App() {
  const [active, setActive] = useState("transactions");
  const [navigationTarget, setNavigationTarget] = useState(null);

  const pageTitle = NAV_ITEMS.find((n) => n.key === active)?.label ?? "";

  const renderPage = () => {
    switch (active) {
      case "dashboard":
        return <DashboardPage />;
      case "transactions":
        return (
          <TransactionsPage
            focusTransactionId={navigationTarget?.type === "TRANSACTION" ? navigationTarget.id : null}
            focusRequestId={navigationTarget?.requestId}
          />
        );
      case "monitor":
        return <MonitoringRules />;
      case "alerts":
        return (
          <AlertsPage
            focusAlertId={navigationTarget?.type === "ALERT" ? navigationTarget.id : null}
            focusRequestId={navigationTarget?.requestId}
          />
        );
      default:
        return null;
    }
  };

  const handleNotificationNavigation = (action) => {
    if (!action?.targetId || !action?.targetType) return;
    const page = action.targetType === "ALERT" ? "alerts" : "transactions";
    setNavigationTarget({
      type: action.targetType,
      id: action.targetId,
      requestId: Date.now(),
    });
    setActive(page);
  };

  return (
    <div style={{ display: "flex", height: "100vh", fontFamily: "Inter, system-ui, sans-serif", background: COLORS.canvas }}>
      <Sidebar active={active} onChange={setActive} />

      <main style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <header
          style={{
            height: 54,
            flexShrink: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            padding: "0 24px",
            background: COLORS.card,
            borderBottom: `1px solid ${COLORS.border}`,
          }}
        >
          <span style={{ fontSize: 15, fontWeight: 600, color: COLORS.ink }}>{pageTitle}</span>
          <NotificationCenter onNavigate={handleNotificationNavigation} />
        </header>
        <div style={{ flex: 1, overflow: "auto", padding: 24 }}>
          {renderPage()}
        </div>
      </main>
    </div>
  );
}
