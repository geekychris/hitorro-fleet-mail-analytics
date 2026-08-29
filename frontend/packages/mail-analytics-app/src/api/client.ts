export async function api<T = any>(path: string, init?: RequestInit): Promise<T> {
  const resp = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
    ...init
  });
  if (!resp.ok) throw new Error(`${resp.status} ${resp.statusText}: ${await resp.text()}`);
  const ct = resp.headers.get('content-type') || '';
  return ct.includes('application/json') ? await resp.json() : (await resp.text() as any);
}

export const dashboard = {
  overview: (from?: string, to?: string) => api<any>(`/api/dashboard/overview${qs({ from, to })}`),
  histogram: (bucket = 'day', from?: string, to?: string) =>
    api<Array<{ at: string; count: number }>>(`/api/dashboard/histogram${qs({ bucket, from, to })}`),
  topSenders: (from?: string, to?: string, limit = 20) =>
    api<Array<{ value: string; count: number }>>(`/api/dashboard/top-senders${qs({ from, to, limit })}`),
  topDomains: (from?: string, to?: string, limit = 20) =>
    api<Array<{ value: string; count: number }>>(`/api/dashboard/top-domains${qs({ from, to, limit })}`),
  topEntities: (kind = 'PERSON', from?: string, to?: string, limit = 20) =>
    api<Array<{ value: string; count: number }>>(`/api/dashboard/top-entities${qs({ kind, from, to, limit })}`),
  actionCandidates: (limit = 20) => api<any>(`/api/dashboard/action-candidates${qs({ limit })}`),
  trends: (window = '7d') => api<any>(`/api/dashboard/trends${qs({ window })}`)
};

export const search = {
  mail: (q: string, opts?: { from?: string; to?: string; offset?: number; limit?: number; sort?: string }) =>
    api<any>(`/api/search/mail${qs({ q, ...opts })}`)
};

export const sender = {
  profile: (email: string, from?: string, to?: string) =>
    api<any>(`/api/senders/${encodeURIComponent(email)}${qs({ from, to })}`),
  messages: (email: string, from?: string, to?: string, offset = 0, limit = 50) =>
    api<any>(`/api/senders/${encodeURIComponent(email)}/messages${qs({ from, to, offset, limit })}`)
};

export const domain = {
  profile: (d: string, from?: string, to?: string) =>
    api<any>(`/api/domains/${encodeURIComponent(d)}${qs({ from, to })}`),
  senders: (d: string, from?: string, to?: string, limit = 50) =>
    api<Array<{ value: string; count: number }>>(`/api/domains/${encodeURIComponent(d)}/senders${qs({ from, to, limit })}`)
};

export const topics = {
  entities: (from?: string, to?: string, limit = 20) =>
    api<any>(`/api/topics/entities${qs({ from, to, limit })}`)
};

export const clusters = {
  threads: (from?: string, to?: string, scanLimit = 500) =>
    api<any[]>(`/api/clusters/threads${qs({ from, to, scanLimit })}`)
};

export const savedQueries = {
  list: () => api<any[]>('/api/saved-queries'),
  get: (id: number) => api<any>(`/api/saved-queries/${id}`),
  create: (body: any) => api<any>('/api/saved-queries', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: number, body: any) => api<any>(`/api/saved-queries/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  del: (id: number) => api<void>(`/api/saved-queries/${id}`, { method: 'DELETE' }),
  run: (id: number) => api<any>(`/api/saved-queries/${id}/run`, { method: 'POST' })
};

export const alerts = {
  list: () => api<any[]>('/api/alerts'),
  create: (body: any) => api<any>('/api/alerts', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: number, body: any) => api<any>(`/api/alerts/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  del: (id: number) => api<void>(`/api/alerts/${id}`, { method: 'DELETE' }),
  runNow: (id: number) => api<any>(`/api/alerts/${id}/run-now`, { method: 'POST' }),
  mute: (id: number, minutes: number) => api<any>(`/api/alerts/${id}/mute${qs({ minutes })}`, { method: 'POST' }),
  enable: (id: number, enabled: boolean) => api<any>(`/api/alerts/${id}/enable${qs({ enabled })}`, { method: 'POST' }),
  firings: (id: number, page = 0, size = 50) => api<any[]>(`/api/alerts/${id}/firings${qs({ page, size })}`),
  allFirings: (page = 0, size = 50) => api<any[]>(`/api/alerts/firings${qs({ page, size })}`)
};

export const inbox = {
  list: (page = 0, size = 100) => api<any[]>(`/api/inbox${qs({ page, size })}`),
  unreadCount: () => api<{ count: number }>('/api/inbox/unread-count'),
  read: (id: number) => api<void>(`/api/inbox/${id}/read`, { method: 'POST' }),
  dismiss: (id: number) => api<void>(`/api/inbox/${id}/dismiss`, { method: 'POST' }),
  snooze: (id: number, minutes = 60) => api<any>(`/api/inbox/${id}/snooze${qs({ minutes })}`, { method: 'POST' })
};

export const reports = {
  list: () => api<any[]>('/api/reports'),
  create: (body: any) => api<any>('/api/reports', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: number, body: any) => api<any>(`/api/reports/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  del: (id: number) => api<void>(`/api/reports/${id}`, { method: 'DELETE' }),
  runNow: (id: number) => api<any>(`/api/reports/${id}/run-now`, { method: 'POST' }),
  runs: (id: number) => api<any[]>(`/api/reports/${id}/runs`)
};

export const suggestions = {
  list: () => api<any[]>('/api/suggestions'),
  runNow: () => api<{ emitted: number }>('/api/suggestions/run-now', { method: 'POST' }),
  dismiss: (id: number) => api<any>(`/api/suggestions/${id}/dismiss`, { method: 'POST' }),
  reviewed: (id: number) => api<any>(`/api/suggestions/${id}/reviewed`, { method: 'POST' })
};

export const ingest = {
  sources: () => api<any[]>('/api/ingest/sources'),
  status: (id: string) => api<any>(`/api/ingest/sources/${encodeURIComponent(id)}/status`),
  runOnce: (id: string) => api<any>(`/api/ingest/sources/${encodeURIComponent(id)}/run`, { method: 'POST' }),
  backfill: (id: string, daysBack?: number) =>
    api<any>(`/api/ingest/sources/${encodeURIComponent(id)}/backfill${qs({ daysBack })}`, { method: 'POST' })
};

export const webhooks = {
  list: () => api<any[]>('/api/webhooks'),
  create: (body: any) => api<any>('/api/webhooks', { method: 'POST', body: JSON.stringify(body) }),
  update: (id: number, body: any) => api<any>(`/api/webhooks/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  del: (id: number) => api<void>(`/api/webhooks/${id}`, { method: 'DELETE' }),
  test: (id: number) => api<any>(`/api/webhooks/${id}/test`, { method: 'POST' })
};

export const settings = { get: () => api<any>('/api/settings') };
export const health = () => api<any>('/api/health');
export const messages = {
  get: (id: string) => api<any>(`/api/messages/${encodeURIComponent(id)}`)
};

export interface SummaryStyle { id: string; label: string; description: string; }
export const summary = {
  styles: () => api<SummaryStyle[]>('/api/summary/styles'),
  thread: (key: string, style: string, opts?: { from?: string; to?: string; model?: string }) =>
    api<any>(`/api/summary/thread${qs({ key, style, ...opts })}`, { method: 'POST' }),
  entity: (value: string, style: string, opts?: { kind?: string; from?: string; to?: string; model?: string }) =>
    api<any>(`/api/summary/entity${qs({ value, style, ...opts })}`, { method: 'POST' })
};

function qs(obj: Record<string, any> | undefined) {
  if (!obj) return '';
  const params = Object.entries(obj)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`);
  return params.length ? '?' + params.join('&') : '';
}
