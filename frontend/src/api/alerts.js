const BASE = "/api/alerts";

async function parseError(res) {
  let message = `Request failed (${res.status})`;
  try {
    const body = await res.json();
    if (body?.message) message = body.message;
    else if (body?.error) message = body.error;
  } catch {
    // ignore non-JSON error bodies
  }
  const err = new Error(message);
  err.status = res.status;
  throw err;
}

function buildQuery(params = {}) {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    qs.set(key, String(value));
  });
  const query = qs.toString();
  return query ? `?${query}` : "";
}

/** GET /api/alerts */
export async function fetchAlerts(params = {}) {
  const res = await fetch(`${BASE}${buildQuery(params)}`);
  if (!res.ok) await parseError(res);
  return res.json();
}

/** GET /api/alerts/{id} */
export async function fetchAlert(alertId) {
  const res = await fetch(`${BASE}/${alertId}`);
  if (!res.ok) await parseError(res);
  return res.json();
}

/** PATCH /api/alerts/{id}/status */
export async function updateAlertStatus(alertId, body) {
  const res = await fetch(`${BASE}/${alertId}/status`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}

/** GET /api/alerts/{id}/history */
export async function fetchAlertHistory(alertId) {
  const res = await fetch(`${BASE}/${alertId}/history`);
  if (!res.ok) await parseError(res);
  return res.json();
}
