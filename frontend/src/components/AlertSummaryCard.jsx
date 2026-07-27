import { COLORS } from "../constants/theme";

export default function AlertSummaryCard({ label, value, color, bg }) {
    return (
        <div
            style={{
                background: COLORS.card,
                border: `1px solid ${COLORS.border}`,
                borderRadius: 12,
                padding: "16px 18px",
                flex: 1,
                minWidth: 180,
                display: "flex",
                alignItems: "center",
                gap: 14,
            }}
        >
            <div
                style={{
                    width: 40,
                    height: 40,
                    borderRadius: 10,
                    background: bg,
                    flexShrink: 0,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                }}
            >
                <span style={{ width: 10, height: 10, borderRadius: "50%", background: color }} />
            </div>
            <div>
                <div style={{ fontSize: 12, color: COLORS.slate, marginBottom: 2 }}>{label}</div>
                <div
                    style={{
                        fontFamily: "'IBM Plex Mono', monospace",
                        fontSize: 22,
                        fontWeight: 600,
                        color: COLORS.ink,
                    }}
                >
                    {value}
                </div>
            </div>
        </div>
    );
}
