import { COLORS } from "./theme";

export const antdTheme = {
  token: {
    colorPrimary: COLORS.accent,
    colorSuccess: COLORS.green,
    colorWarning: COLORS.amber,
    colorError: COLORS.red,
    colorInfo: COLORS.accent,
    borderRadius: 8,
    fontFamily: "Inter, system-ui, sans-serif",
    colorTextBase: COLORS.ink,
  },
  components: {
    Table: {
      headerBg: COLORS.canvas,
      headerColor: COLORS.slate,
      borderColor: COLORS.border,
    },
    Modal: {
      titleFontSize: 16,
    },
  },
};
