import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ingest, settings, webhooks } from '../api/client';
import Tile from '../components/Tile';

export default function Settings() {
  const qc = useQueryClient();
  const { data: s } = useQuery({ queryKey: ['settings'], queryFn: settings.get });
  const { data: sources } = useQuery({ queryKey: ['ingest-sources'], queryFn: ingest.sources });
  const { data: hooks } = useQuery({ queryKey: ['webhooks'], queryFn: webhooks.list });
  const runOnce = useMutation({
    mutationFn: (id: string) => ingest.runOnce(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ingest-sources'] })
  });
  const backfill = useMutation({
    mutationFn: ({ id, daysBack }: { id: string; daysBack: number }) => ingest.backfill(id, daysBack),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ingest-sources'] })
  });

  return (
    <div className="grid grid-cols-2 gap-4">
      <Tile title="deployment">
        <pre className="text-xs bg-surface p-3 rounded overflow-auto">{JSON.stringify(s, null, 2)}</pre>
      </Tile>
      <Tile title="ingest sources">
        {sources?.length ? (
          <div className="space-y-2 text-sm">
            {sources.map((src: any) => (
              <div key={src.id} className="bg-surface border border-border rounded p-2">
                <div className="flex justify-between items-center">
                  <div>
                    <div className="font-semibold">{src.id} <span className="text-xs text-muted">({src.kind})</span></div>
                    <div className="text-xs text-muted">{src.healthDetail}</div>
                    <div className="text-xs text-muted">
                      last row: {src.lastRowId ?? '—'} · total: {src.totalIngested} · last run: {src.lastRunAt ? new Date(src.lastRunAt).toLocaleString() : '—'}
                    </div>
                    {src.lastError && <div className="text-xs text-crit mt-1">{src.lastError}</div>}
                  </div>
                  <div className="flex flex-col gap-1 text-xs">
                    <button className="text-accent" onClick={() => runOnce.mutate(src.id)}>run once</button>
                    <button className="text-accent" onClick={() => backfill.mutate({ id: src.id, daysBack: 90 })}>backfill 90d</button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : <div className="text-muted text-sm">no sources configured</div>}
      </Tile>
      <Tile title="webhooks">
        {hooks?.length ? (
          <div className="divide-y divide-border text-sm">
            {hooks.map((h: any) => (
              <div key={h.id} className="py-2">
                <div className="font-semibold">{h.name}</div>
                <div className="text-xs text-muted">{h.url}</div>
              </div>
            ))}
          </div>
        ) : <div className="text-muted text-sm">no webhooks — POST /api/webhooks to add one</div>}
      </Tile>
      <Tile title="delivery config">
        <pre className="text-xs bg-surface p-3 rounded overflow-auto">{JSON.stringify(s?.delivery, null, 2)}</pre>
      </Tile>
    </div>
  );
}
