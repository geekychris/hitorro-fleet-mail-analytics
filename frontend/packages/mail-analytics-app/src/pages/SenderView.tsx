import { useQuery } from '@tanstack/react-query';
import { useParams, Link } from 'react-router-dom';
import { sender } from '../api/client';
import { useAnalyticsStore } from '../state/store';
import StatBox from '../components/StatBox';
import Tile from '../components/Tile';

export default function SenderView() {
  const { email = '' } = useParams();
  const range = useAnalyticsStore((s) => s.effectiveRange());
  const { data } = useQuery({
    queryKey: ['sender', email, range],
    queryFn: () => sender.profile(email, range.from, range.to)
  });
  if (!data) return <div className="text-muted">loading…</div>;
  return (
    <div className="space-y-4">
      <div>
        <div className="text-xs text-muted">sender</div>
        <div className="text-2xl font-bold">{email}</div>
        <Link to={`/domains/${encodeURIComponent(data.domain)}`} className="text-accent text-sm">{data.domain}</Link>
      </div>
      <div className="grid grid-cols-3 gap-4">
        <StatBox label="total" value={data.total} />
        <StatBox label="unread" value={data.unread} />
        <StatBox label="flagged" value={data.flagged} />
      </div>
      <Tile title="recent messages">
        {data.recent?.documents?.length ? (
          <div className="divide-y divide-border text-sm">
            {data.recent.documents.map((d: any, i: number) => (
              <div key={i} className="py-2">
                <div className="font-semibold">{d.title?.mls?.[0]?.text ?? '(no subject)'}</div>
                <div className="text-xs text-muted">{d.times?.date_received ? new Date(d.times.date_received).toLocaleString() : ''}</div>
              </div>
            ))}
          </div>
        ) : <div className="text-muted text-sm">no messages in window</div>}
      </Tile>
    </div>
  );
}
