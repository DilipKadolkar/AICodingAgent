const BASE = '/api';

async function handleResponse(response) {
  if (!response.ok) {
    let body = null;
    try {
      body = await response.json();
    } catch {
      // no JSON body
    }
    const message = body?.details || body?.error || `Request failed with status ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    error.code = body?.error;
    throw error;
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

export async function checkHealth() {
  const res = await fetch(`${BASE}/health`);
  return handleResponse(res);
}

export async function checkOllamaHealth() {
  const res = await fetch(`${BASE}/health/ollama`);
  return handleResponse(res);
}

export async function listSessions() {
  const res = await fetch(`${BASE}/sessions`);
  return handleResponse(res);
}

export async function createSession(repositoryPath, title) {
  const res = await fetch(`${BASE}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ repositoryPath, title }),
  });
  return handleResponse(res);
}

export async function getSession(id) {
  const res = await fetch(`${BASE}/sessions/${id}`);
  return handleResponse(res);
}

export async function postMessage(id, content) {
  const res = await fetch(`${BASE}/sessions/${id}/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
  });
  return handleResponse(res);
}

export async function endSession(id) {
  const res = await fetch(`${BASE}/sessions/${id}/end`, { method: 'POST' });
  return handleResponse(res);
}

export async function getSummary(id) {
  const res = await fetch(`${BASE}/sessions/${id}/summary`);
  return handleResponse(res);
}

/**
 * Opens the session's SSE stream. Returns a function to close it.
 * onUpdate receives the full SessionDetailDto whenever it changes;
 * onStatus receives the raw status string whenever it changes.
 */
export function streamSession(id, { onUpdate, onStatus, onError, onDone }) {
  const source = new EventSource(`${BASE}/sessions/${id}/stream`);

  source.addEventListener('update', (event) => {
    try {
      onUpdate?.(JSON.parse(event.data));
    } catch (e) {
      onError?.(e);
    }
  });
  source.addEventListener('status', (event) => {
    onStatus?.(event.data);
  });
  source.onerror = () => {
    onError?.(new Error('Connection to the live update stream was lost.'));
    source.close();
    onDone?.();
  };

  return () => source.close();
}
