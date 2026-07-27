const BASE = "/api/transactions";

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

/** GET /api/transactions */
export async function fetchTransactions(params = {}) {
  const res = await fetch(`${BASE}${buildQuery(params)}`);
  if (!res.ok) await parseError(res);
  return res.json();
}

/** GET /api/transactions/{id} */
export async function fetchTransaction(transactionId) {
  const res = await fetch(`${BASE}/${transactionId}`);
  if (!res.ok) await parseError(res);
  return res.json();
}

/** POST /api/transactions */
export async function createTransaction(body) {
  const res = await fetch(BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) await parseError(res);
  return res.json();
}
