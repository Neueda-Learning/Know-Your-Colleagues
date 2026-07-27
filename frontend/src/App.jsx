import { useState } from "react";
import Sidebar from "./components/Sidebar";
import TransactionsPage from "./pages/TransactionsPage";
import MonitoringRules from "./pages/MonitoringRules.jsx";
import AlertsPage from "./pages/AlertsPage";
import DashboardPage from "./pages/DashboardPage";
import { COLORS, NAV_ITEMS } from "./constants/theme";
import "./App.css";

export default function App() {
  const [active, setActive] = useState("transactions");

  const pageTitle = NAV_ITEMS.find((n) => n.key === active)?.label ?? "";

  const renderPage = () => {
    switch (active) {
      case "dashboard":
        return <DashboardPage/>;
      case "transactions":
        return <TransactionsPage />;
      case "monitor":
        return <MonitoringRules />;
      case "alerts":
        return <AlertsPage />;

      default:
        return null;
    }
  };

  return (
    <div style={{ display: "flex", height: "100vh", fontFamily: "Inter, system-ui, sans-serif", background: COLORS.canvas }}>
      <Sidebar active={active} onChange={setActive} />

      <main style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>

        <div style={{ flex: 1, overflow: "auto", padding: 24 }}>
          <h1 style={{ fontSize: 18, fontWeight: 600, color: COLORS.ink, margin: "0 0 16px" }}>{pageTitle}</h1>
          {renderPage()}
        </div>
      </main>
    </div>
  );
}
