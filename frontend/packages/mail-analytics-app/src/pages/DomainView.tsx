import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { domain } from '../api/client';
import { useAnalyticsStore } from '../state/store';
import StatBox from '../components/StatBox';
import Tile from '../components/Tile';
import TopNTable from '../components/TopNTable';

export default function DomainView() {
  const { domain: d = '' } = useParams();
  const range = useAnalyticsStore((s) => s.effectiveRange());
  const { data } = useQuery({
    queryKey: ['domain', d, range],
    queryFn: () => domain.profile(d, range.from, range.to)
  });
  if (!data) return <div className="text-muted">loading…</div>;
  return (
    <div className="space-y-4">
      <div><div className="text-xs text-muted">domain</div><div className="text-2xl font-bold">{d}</div></div>
      <div className="grid grid-cols-2 gap-4">
        <StatBox label="total messages" value={data.total} />
        <StatBox label="unique senders" value={data.senders?.length ?? 0} />
      </div>
      <Tile title="senders at this domain">
        <TopNTable rows={data.senders} linkTo={(v) => `/senders/${encodeURIComponent(v)}`} />
      </Tile>
      <Tile title="recent messages">
        {data.recent?.documents?.length ? (
          <div className="divide-y divide-border text-sm">
            {data.recent.documents.map((doc: any, i: number) => (
              <div key={i} className="py-2">
                <div className="font-semibold">{doc.title?.mls?.[0]?.text ?? '(no subject)'}</div>
                <div className="text-xs text-muted">{doc.sender_address}</div>
              </div>
            ))}
          </div>
        ) : <div className="text-muted text-sm">no messages in window</div>}
      </Tile>
    </div>
  );
}
