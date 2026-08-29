import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { search } from '../api/client';
import { useAnalyticsStore } from '../state/store';
import Tile from '../components/Tile';

export default function Search() {
  const [params, setParams] = useSearchParams();
  const [text, setText] = useState(params.get('q') ?? '');
  const range = useAnalyticsStore((s) => s.effectiveRange());
  const q = params.get('q') ?? '';

  const { data } = useQuery({
    queryKey: ['search', q, range],
    queryFn: () => search.mail(q, { from: range.from, to: range.to, limit: 50, sort: 'date_received:desc' }),
    enabled: q.length > 0 || true
  });

  return (
    <div className="space-y-4">
      <form
        onSubmit={(e) => { e.preventDefault(); setParams({ q: text }); }}
        className="flex gap-2"
      >
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="search mail (Lucene syntax OK, e.g. sender_domain:substack.com AND body.mls.clean:kubernetes)"
          className="flex-1 bg-panel border border-border rounded px-3 py-2 text-sm focus:outline-none focus:border-accent"
        />
        <button className="bg-accent text-surface font-semibold px-4 py-2 rounded text-sm">search</button>
      </form>

      <div className="grid grid-cols-4 gap-4">
        <div className="col-span-3">
          <Tile title={`results (${data?.totalHits ?? 0})`}>
            {data?.documents?.length ? (
              <div className="divide-y divide-border">
                {data.documents.map((doc: any, i: number) => <MailRow key={i} doc={doc} />)}
              </div>
            ) : (
              <div className="text-muted text-sm">no results</div>
            )}
          </Tile>
        </div>
        <div className="col-span-1 space-y-4">
          <Tile title="sender domains"><Facets facets={data?.facets?.sender_domain?.values} onPick={(v) => setParams({ q: `${q} sender_domain:"${v}"` })} /></Tile>
          <Tile title="senders"><Facets facets={data?.facets?.sender_address?.values} onPick={(v) => setParams({ q: `${q} sender_address:"${v}"` })} /></Tile>
        </div>
      </div>
    </div>
  );
}

function MailRow({ doc }: { doc: any }) {
  const subject = doc.title?.mls?.[0]?.text ?? '(no subject)';
  const preview = doc.body?.mls?.[0]?.clean ?? doc.body?.mls?.[0]?.text ?? '';
  const from = doc.sender_address ?? '';
  const domain = doc.sender_domain ?? '';
  const dateMs = doc.times?.date_received;
  return (
    <div className="py-3">
      <div className="flex justify-between items-baseline">
        <div className="font-semibold">{subject}</div>
        <div className="text-xs text-muted">{dateMs ? new Date(dateMs).toLocaleString() : ''}</div>
      </div>
      <div className="text-xs text-muted mt-1">
        <Link to={`/senders/${encodeURIComponent(from)}`} className="text-accent">{from}</Link>
        {' • '}
        <Link to={`/domains/${encodeURIComponent(domain)}`} className="text-accent">{domain}</Link>
      </div>
      {preview && <div className="text-sm text-muted mt-1 line-clamp-2">{String(preview).slice(0, 200)}</div>}
    </div>
  );
}

function Facets({ facets, onPick }: { facets?: Array<{ value: string; count: number }>; onPick: (v: string) => void }) {
  if (!facets || facets.length === 0) return <div className="text-muted text-xs">—</div>;
  return (
    <div className="space-y-1 text-sm">
      {facets.slice(0, 15).map((f) => (
        <button key={f.value} onClick={() => onPick(f.value)} className="flex justify-between w-full hover:bg-surface px-2 py-1 rounded text-left">
          <span className="truncate">{f.value}</span>
          <span className="text-muted tabular-nums text-xs">{f.count}</span>
        </button>
      ))}
    </div>
  );
}
