import { Receipt, ShieldAlert, BellRing, LayoutDashboard } from "lucide-react";

export const COLORS = {
  navy: "#0B1220",
  navySoft: "#16233B",
  navyBorder: "#22314D",
  canvas: "#F5F8FC",
  card: "#FFFFFF",
  border: "#E2E8F0",
  ink: "#16213B",
  slate: "#64748B",
  accent: "#2F6FED",
  accentSoft: "#E8F0FE",
  green: "#16A34A",
  greenSoft: "#E7F6EC",
  red: "#DC2626",
  redSoft: "#FCEAEA",
  amber: "#D97706",
  amberSoft: "#FDF3E3",
};

export const NAV_ITEMS = [
  { key: "dashboard", label: "Dashboard", icon:  LayoutDashboard},
  { key: "transactions", label: "Transaction History", icon: Receipt },
  { key: "alerts", label: "Alert History", icon: BellRing},
  { key: "monitor", label: "Monitoring Rules", icon: ShieldAlert },

];
