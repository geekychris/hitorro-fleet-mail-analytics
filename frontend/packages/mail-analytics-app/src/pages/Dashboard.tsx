import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Link } from 'react-router-dom';
import { dashboard } from '../api/client';
import { useAnalyticsStore } from '../state/store';
import StatBox from '../components/StatBox';
import Tile from '../components/Tile';
import TopNTable from '../components/TopNTable';

export default function Dashboard() {
  const range = useAnalyticsStore((s) => s.effectiveRange());
  const { data: overview } = useQuery({
    queryKey: ['dashboard-overview', range],
    queryFn: () => dashboard.overview(range.from, range.to)
  });
  const { data: histogram } = useQuery({
    queryKey: ['dashboard-histogram', range],
    queryFn: () => dashboard.histogram('day', range.from, range.to)
  });

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-4 gap-4">
        <StatBox label="total" value={overview?.total ?? 0} />
        <StatBox label="unread" value={overview?.unread ?? 0} />
        <StatBox label="flagged" value={overview?.flagged ?? 0} />
        <StatBox label="newsletters" value={overview?.newsletters ?? 0} />
      </div>

      <Tile title="messages over time (day)">
        <div style={{ width: '100%', height: 240 }}>
          <ResponsiveContainer>
            <BarChart data={histogram ?? []}>
              <XAxis dataKey="at" tickFormatter={(v) => new Date(v).toLocaleDateString()} stroke="#94a3b8" />
              <YAxis stroke="#94a3b8" />
              <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid #334155' }} />
              <Bar dataKey="count" fill="#38bdf8" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Tile>

      <div className="grid grid-cols-2 gap-4">
        <Tile title="top senders">
          <TopNTable rows={overview?.topSenders} linkTo={(v) => `/senders/${encodeURIComponent(v)}`} />
        </Tile>
        <Tile title="top domains">
          <TopNTable rows={overview?.topDomains} linkTo={(v) => `/domains/${encodeURIComponent(v)}`} />
        </Tile>
        <Tile title="top persons (NER)">
          <TopNTable rows={overview?.topPersons} />
        </Tile>
        <Tile title="top organizations (NER)">
          <TopNTable rows={overview?.topOrgs} />
        </Tile>
      </div>

      <Tile title="action candidates (unread, non-newsletter)"
            action={<Link to="/search?q=read:false" className="text-xs text-accent">see all →</Link>}>
        {overview?.actionCandidates?.documents?.length ? (
          <div className="divide-y divide-border">
            {overview.actionCandidates.documents.map((doc: any, i: number) => (
              <div key={i} className="py-2 text-sm">
                <div className="font-semibold truncate">{doc.title?.mls?.[0]?.text ?? '(no subject)'}</div>
                <div className="text-muted text-xs">{doc.sender_address}</div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-muted text-sm">no action candidates in window</div>
        )}
      </Tile>
    </div>
  );
}
