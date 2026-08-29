import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { reports } from '../api/client';
import Tile from '../components/Tile';

export default function Reports() {
  const qc = useQueryClient();
  const { data: list } = useQuery({ queryKey: ['reports'], queryFn: reports.list });
  const [editing, setEditing] = useState<any | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const { data: runs } = useQuery({
    queryKey: ['report-runs', selectedId],
    queryFn: () => reports.runs(selectedId!),
    enabled: selectedId != null
  });
  const save = useMutation({
    mutationFn: (r: any) => r.id ? reports.update(r.id, r) : reports.create(r),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['reports'] }); setEditing(null); }
  });
  const runNow = useMutation({
    mutationFn: reports.runNow,
    onSuccess: (_data, id) => { setSelectedId(id); qc.invalidateQueries({ queryKey: ['report-runs', id] }); }
  });
  const del = useMutation({ mutationFn: reports.del, onSuccess: () => qc.invalidateQueries({ queryKey: ['reports'] }) });

  return (
    <div className="grid grid-cols-2 gap-4">
      <div className="space-y-4">
        <Tile title="reports"
              action={<button onClick={() => setEditing({ name: '', kind: 'DASHBOARD_SNAPSHOT', configJson: '{}', enabled: true, retentionDays: 90 })}
                              className="text-xs bg-accent text-surface font-semibold px-2 py-1 rounded">new</button>}>
          {list?.length ? (
            <div className="divide-y divide-border text-sm">
              {list.map((r: any) => (
                <div key={r.id} className="py-2 flex justify-between">
                  <div>
                    <div className="font-semibold cursor-pointer" onClick={() => setSelectedId(r.id)}>{r.name}</div>
                    <div className="text-xs text-muted">{r.kind} · {r.cron ?? 'on-demand'}</div>
                  </div>
                  <div className="flex gap-1 text-xs">
                    <button className="text-accent" onClick={() => runNow.mutate(r.id)}>run</button>
                    <button className="text-accent" onClick={() => setEditing(r)}>edit</button>
                    <button className="text-crit" onClick={() => del.mutate(r.id)}>del</button>
                  </div>
                </div>
              ))}
            </div>
          ) : <div className="text-muted text-sm">no reports</div>}
        </Tile>

        <Tile title="editor">
          {editing ? (
            <div className="space-y-2 text-sm">
              <input className="w-full bg-surface border border-border rounded px-2 py-1"
                     placeholder="name" value={editing.name}
                     onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
              <select className="w-full bg-surface border border-border rounded px-2 py-1"
                      value={editing.kind}
                      onChange={(e) => setEditing({ ...editing, kind: e.target.value })}>
                <option>DASHBOARD_SNAPSHOT</option>
                <option>SAVED_QUERY_SET</option>
                <option>CUSTOM</option>
              </select>
              <input className="w-full bg-surface border border-border rounded px-2 py-1"
                     placeholder="cron (optional)" value={editing.cron ?? ''}
                     onChange={(e) => setEditing({ ...editing, cron: e.target.value })} />
              <textarea className="w-full bg-surface border border-border rounded px-2 py-1 font-mono text-xs" rows={5}
                        placeholder='config JSON — e.g. {"savedQueryIds":[1,2,3]}'
                        value={editing.configJson}
                        onChange={(e) => setEditing({ ...editing, configJson: e.target.value })} />
              <div className="flex gap-2">
                <button className="bg-accent text-surface font-semibold px-3 py-1 rounded" onClick={() => save.mutate(editing)}>save</button>
                <button className="text-muted" onClick={() => setEditing(null)}>cancel</button>
              </div>
            </div>
          ) : <div className="text-muted text-sm">click new or edit</div>}
        </Tile>
      </div>

      <Tile title={selectedId ? `runs for report ${selectedId}` : 'runs'}>
        {selectedId && runs?.length ? (
          <div className="divide-y divide-border text-sm">
            {runs.map((rr: any) => (
              <div key={rr.id} className="py-2">
                <div className="flex justify-between">
                  <div className="text-xs">
                    <span className={rr.status === 'SUCCESS' ? 'text-accent' : rr.status === 'FAILED' ? 'text-crit' : 'text-warn'}>{rr.status}</span>
                    <span className="text-muted ml-2">{new Date(rr.startedAt).toLocaleString()}</span>
                  </div>
                  {rr.artifactPath && (
                    <a className="text-accent text-xs"
                       href={`/api/reports/${rr.reportId}/runs/${rr.id}/artifact`} target="_blank" rel="noreferrer">artifact</a>
                  )}
                </div>
                {rr.errorMessage && <div className="text-xs text-crit mt-1">{rr.errorMessage}</div>}
              </div>
            ))}
          </div>
        ) : <div className="text-muted text-sm">{selectedId ? 'no runs yet' : 'pick a report'}</div>}
      </Tile>
    </div>
  );
}
