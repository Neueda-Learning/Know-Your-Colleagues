import { COLORS, NAV_ITEMS } from "../constants/theme";

export default function Sidebar({ active, onChange }) {
  return (
    <aside style={{ width: 220, background: COLORS.navy, display: "flex", flexDirection: "column", flexShrink: 0 }}>
      <div
        style={{
          padding: "20px 20px 16px",
          display: "flex",
          alignItems: "center",
          gap: 10,
          borderBottom: `1px solid ${COLORS.navyBorder}`,
        }}
      >
        <span style={{ color: "#fff", fontSize: 14, fontWeight: 600 }}>KNOW YOUR COLLEAGUES</span>
      </div>

      <nav style={{ padding: "12px 10px", display: "flex", flexDirection: "column", gap: 2 }}>
        {NAV_ITEMS.map((item) => {
          const Icon = item.icon;
          const isActive = active === item.key;
          return (
            <button
              key={item.key}
              onClick={() => onChange(item.key)}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 10,
                padding: "9px 12px",
                border: "none",
                background: isActive ? COLORS.navySoft : "transparent",
                color: isActive ? "#fff" : "#8FA0BF",
                fontSize: 13.5,
                cursor: "pointer",
                textAlign: "left",
                borderLeft: isActive ? `3px solid ${COLORS.accent}` : "3px solid transparent",
                borderRadius: 0,
              }}
            >
              <Icon size={16} />
              <span style={{ flex: 1 }}>{item.label}</span>
              {item.badge ? (
                <span
                  style={{
                    background: COLORS.red,
                    color: "#fff",
                    fontSize: 11,
                    borderRadius: 999,
                    padding: "1px 6px",
                    fontFamily: "'IBM Plex Mono', monospace",
                  }}
                >
                  {item.badge}
                </span>
              ) : null}
            </button>
          );
        })}
      </nav>
    </aside>
  );
}
