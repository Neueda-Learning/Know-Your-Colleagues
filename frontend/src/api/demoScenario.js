const BASE = "/api/demo/scenario";

async function parseResponse(response) {
  if (response.ok) return response.json();

  let message = `Request failed (${response.status})`;
  try {
    const body = await response.json();
    if (body?.message) message = body.message;
  } catch {
    // Ignore non-JSON error bodies.
  }
  const error = new Error(message);
  error.status = response.status;
  throw error;
}

export async function fetchDemoScenarioStatus() {
  return parseResponse(await fetch(BASE));
}

export async function startDemoScenario() {
  return parseResponse(await fetch(BASE, { method: "POST" }));
}
