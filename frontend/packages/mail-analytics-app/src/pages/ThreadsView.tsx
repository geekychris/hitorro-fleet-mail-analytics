import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { clusters, messages, summary } from '../api/client';
import { useAnalyticsStore } from '../state/store';
import Tile from '../components/Tile';

interface ThreadMsg {
  id: any;
  subject: string;
  sender: string;
  date: number;
}
interface Cluster {
  key: string;
  subject: string;
  messageCount: number;
  messages: ThreadMsg[];
}

// Stats derived client-side from the message list. Cheap; the backend
// already ships every message per cluster.
function computeStats(c: Cluster) {
  const dates = c.messages.map((m) => m.date).filter((d) => d > 0).sort((a, b) => a - b);
  const senders = new Map<string, number>();
  const domains = new Map<string, number>();
  for (const m of c.messages) {
    if (m.sender) {
      senders.set(m.sender, (senders.get(m.sender) || 0) + 1);
      const at = m.sender.lastIndexOf('@');
      if (at >= 0) {
        const d = m.sender.slice(at + 1).toLowerCase();
        domains.set(d, (domains.get(d) || 0) + 1);
      }
    }
  }
  const first = dates.length ? new Date(dates[0]) : null;
  const last = dates.length ? new Date(dates[dates.length - 1]) : null;
  const span = first && last ? last.getTime() - first.getTime() : 0;
  const avgGap = dates.length > 1 ? span / (dates.length - 1) : 0;
  return {
    first, last,
    spanDays: span / (1000 * 60 * 60 * 24),
    avgGapHours: avgGap / (1000 * 60 * 60),
    uniqueSenders: senders.size,
    uniqueDomains: domains.size,
    topSenders: [...senders.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5),
    topDomains: [...domains.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
  };
}

function fmtDate(d: Date | null) {
  if (!d) return '—';
  return d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function fmtDuration(days: number, hours: number) {
  if (days >= 1) return `${days.toFixed(1)}d`;
  if (hours >= 1) return `${hours.toFixed(1)}h`;
  return `${(hours * 60).toFixed(0)}m`;
}

export default function ThreadsView() {
  const range = useAnalyticsStore((s) => s.effectiveRange());
  const { data } = useQuery({
    queryKey: ['threads', range],
    queryFn: () => clusters.threads(range.from, range.to, 500) as Promise<Cluster[]>
  });
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const filtered = useMemo(() => (data || []).filter((c) => c.messageCount > 1), [data]);
  const selected = useMemo(
    () => filtered.find((c) => c.key === selectedKey) || filtered[0] || null,
    [filtered, selectedKey]
  );
  const stats = selected ? computeStats(selected) : null;

  // --- Summarize wiring ---
  const { data: styles } = useQuery({ queryKey: ['summary-styles'], queryFn: summary.styles });
  const [style, setStyle] = useState<string>('BRIEF');
  const [summaryText, setSummaryText] = useState<string | null>(null);
  const [summaryMeta, setSummaryMeta] = useState<any>(null);
  const summarize = useMutation({
    mutationFn: (params: { key: string; style: string }) =>
      summary.thread(params.key, params.style, { from: range.from, to: range.to }),
    onSuccess: (r) => { setSummaryText(r.summary || r.error || '(no output)'); setSummaryMeta(r); },
    onError: (e: any) => { setSummaryText('error: ' + (e?.message || String(e))); setSummaryMeta(null); }
  });
  // Clear stale summary when switching thread.
  useEffect(() => { setSummaryText(null); setSummaryMeta(null); }, [selected?.key]);

  return (
    <div className="grid grid-cols-5 gap-4">
      <div className="col-span-2">
        <Tile title={`threads (${filtered.length} with 2+ messages)`}>
          {filtered.length ? (
            <div className="divide-y divide-border max-h-[70vh] overflow-auto">
              {filtered.map((c) => (
                <button
                  key={c.key}
                  onClick={() => setSelectedKey(c.key)}
                  className={`w-full py-2 px-2 text-left rounded hover:bg-surface ${
                    (selected && selected.key === c.key) ? 'bg-surface' : ''
                  }`}
                >
                  <div className="flex justify-between items-baseline">
                    <div className="font-medium truncate flex-1">{c.subject || '(no subject)'}</div>
                    <div className="text-xs text-muted tabular-nums ml-2">{c.messageCount}</div>
                  </div>
                </button>
              ))}
            </div>
          ) : (
            <div className="text-muted text-sm">no threaded conversations in window</div>
          )}
        </Tile>
      </div>

      <div className="col-span-3 space-y-4">
        {selected && stats ? (
          <>
            <Tile title={selected.subject || '(no subject)'}>
              <div className="grid grid-cols-3 gap-3 text-sm">
                <Stat label="messages" value={String(selected.messageCount)} />
                <Stat label="participants" value={String(stats.uniqueSenders)} />
                <Stat label="domains" value={String(stats.uniqueDomains)} />
                <Stat label="first" value={fmtDate(stats.first)} />
                <Stat label="last" value={fmtDate(stats.last)} />
                <Stat label="span"
                      value={stats.spanDays > 0
                        ? `${fmtDuration(stats.spanDays, stats.spanDays * 24)} (avg ${fmtDuration(0, stats.avgGapHours)})`
                        : '—'} />
              </div>
            </Tile>

            <div className="grid grid-cols-2 gap-4">
              <Tile title="top senders in thread">
                <div className="space-y-1 text-sm">
                  {stats.topSenders.length === 0 && <div className="text-muted">—</div>}
                  {stats.topSenders.map(([s, n]) => (
                    <div key={s} className="flex justify-between">
                      <Link to={`/senders/${encodeURIComponent(s)}`} className="text-accent truncate">{s}</Link>
                      <span className="text-muted tabular-nums ml-2">{n}</span>
                    </div>
                  ))}
                </div>
              </Tile>
              <Tile title="top domains in thread">
                <div className="space-y-1 text-sm">
                  {stats.topDomains.length === 0 && <div className="text-muted">—</div>}
                  {stats.topDomains.map(([d, n]) => (
                    <div key={d} className="flex justify-between">
                      <Link to={`/domains/${encodeURIComponent(d)}`} className="text-accent truncate">{d}</Link>
                      <span className="text-muted tabular-nums ml-2">{n}</span>
                    </div>
                  ))}
                </div>
              </Tile>
            </div>

            <Tile
              title="summarize"
              action={
                <div className="flex gap-2 items-center text-xs">
                  <select
                    value={style}
                    onChange={(e) => setStyle(e.target.value)}
                    className="bg-surface border border-border rounded px-2 py-1"
                  >
                    {(styles || [{ id: 'BRIEF', label: 'Brief summary', description: '' }]).map((s) => (
                      <option key={s.id} value={s.id} title={s.description}>{s.label}</option>
                    ))}
                  </select>
                  <button
                    disabled={summarize.isPending}
                    onClick={() => summarize.mutate({ key: selected.key, style })}
                    className="bg-accent text-surface font-semibold px-3 py-1 rounded disabled:opacity-50"
                  >
                    {summarize.isPending ? 'thinking…' : 'summarize'}
                  </button>
                </div>
              }
            >
              {summaryText ? (
                <div>
                  <pre className="text-sm whitespace-pre-wrap font-sans">{summaryText}</pre>
                  {summaryMeta && summaryMeta.model && (
                    <div className="text-xs text-muted mt-3">
                      {summaryMeta.style} · {summaryMeta.model} · {summaryMeta.elapsedMs}ms
                      {summaryMeta.messageCount ? ` · ${summaryMeta.messageCount} msgs` : ''}
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-muted text-sm">
                  pick a style and hit summarize — first call warms the model, subsequent are faster
                </div>
              )}
            </Tile>

            <Tile title="messages">
              <div className="divide-y divide-border text-sm">
                {[...selected.messages]
                  .sort((a, b) => a.date - b.date)
                  .map((m, i) => (
                    <MessageRow key={i} msg={m} />
                  ))}
              </div>
            </Tile>
          </>
        ) : (
          <Tile title="thread detail">
            <div className="text-muted text-sm">
              pick a thread from the left to see its participants, timeline, and message list
            </div>
          </Tile>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs text-muted uppercase tracking-wide">{label}</div>
      <div className="text-lg font-semibold">{value}</div>
    </div>
  );
}

/** One message row with a lazy-loaded expandable body. On first expand
 *  we call /api/messages/{id} and cache the response for subsequent
 *  toggles. Useful for cross-referencing the summary against source
 *  text — the user can verify claims (e.g. "the tone is negative")
 *  against the actual message content. */
function MessageRow({ msg }: { msg: any }) {
  const [open, setOpen] = useState(false);
  const rawId = msg?.id?.id ?? msg?.id ?? '';
  const idStr = typeof rawId === 'object' ? JSON.stringify(rawId) : String(rawId);
  const q = useQuery({
    queryKey: ['message', idStr],
    queryFn: () => messages.get(idStr),
    enabled: open && !!idStr
  });
  const body = extractBody(q.data);
  return (
    <div className="py-2">
      <div className="flex justify-between items-baseline">
        <button onClick={() => setOpen(!open)} className="font-medium truncate text-left flex-1 hover:text-accent">
          <span className="text-muted mr-1">{open ? '▼' : '▶'}</span>
          {msg.subject || '(no subject)'}
        </button>
        <div className="text-xs text-muted ml-2">
          {msg.date > 0 ? new Date(msg.date).toLocaleString() : ''}
        </div>
      </div>
      <div className="text-xs text-muted mt-0.5 pl-4">
        {msg.sender ? (
          <Link to={`/senders/${encodeURIComponent(msg.sender)}`} className="text-accent">
            {msg.sender}
          </Link>
        ) : '—'}
      </div>
      {open && (
        <div className="mt-2 pl-4 pr-2">
          {q.isPending && <div className="text-xs text-muted italic">loading…</div>}
          {q.isError && <div className="text-xs text-crit">failed to load: {String((q.error as any)?.message)}</div>}
          {q.data && (
            <>
              {body ? (
                <pre className="text-xs bg-surface p-3 rounded whitespace-pre-wrap font-sans max-h-96 overflow-auto">
{body}
                </pre>
              ) : (
                <div className="text-xs text-muted">(no body text stored for this message)</div>
              )}
              <MessageEntities doc={q.data} />
            </>
          )}
        </div>
      )}
    </div>
  );
}

function extractBody(doc: any): string {
  if (!doc) return '';
  const mls = doc?.body?.mls?.[0];
  if (!mls) return '';
  return (mls.clean || mls.text || '').trim();
}

/** Show NER entities inline so the reader can spot names the summary
 *  claims are present. */
function MessageEntities({ doc }: { doc: any }) {
  const sn: string[] = doc?.body?.mls?.[0]?.segmented_ner || [];
  const tags: Record<string, Set<string>> = { NE_Person: new Set(), NE_Organization: new Set(), NE_Location: new Set(), NE_Date: new Set() };
  const re = /\[\{([^}&]+?)&&(NE_[A-Za-z]+)\}\]/g;
  for (const s of sn) {
    let m: RegExpExecArray | null;
    while ((m = re.exec(s)) !== null) {
      const tag = m[2];
      if (tags[tag]) tags[tag].add(m[1].trim());
    }
  }
  const nonEmpty = Object.entries(tags).filter(([, v]) => v.size > 0);
  if (nonEmpty.length === 0) return null;
  return (
    <div className="text-xs mt-2 flex gap-3 flex-wrap">
      {nonEmpty.map(([kind, values]) => (
        <div key={kind}>
          <span className="text-muted">{kind.replace('NE_', '').toLowerCase()}:</span>{' '}
          <span>{[...values].join(', ')}</span>
        </div>
      ))}
    </div>
  );
}
