import { useEffect, useRef } from "react";

/** Polling interval for list pages (within the requested 5–10s range). */
export const AUTO_REFRESH_INTERVAL_MS = 8_000;

/**
 * Periodically invoke `callback` while the page is visible.
 * Skips ticks when the browser tab is hidden; refreshes once when it becomes visible again.
 */
export function useAutoRefresh(callback, intervalMs = AUTO_REFRESH_INTERVAL_MS) {
  const callbackRef = useRef(callback);

  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  useEffect(() => {
    const tick = () => {
      if (document.visibilityState === "hidden") return;
      callbackRef.current?.();
    };

    const id = window.setInterval(tick, intervalMs);
    const onVisibility = () => {
      if (document.visibilityState === "visible") tick();
    };
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      window.clearInterval(id);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [intervalMs]);
}
