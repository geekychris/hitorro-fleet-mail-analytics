import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { alerts, savedQueries } from '../api/client';
import Tile from '../components/Tile';

export default function Alerts() {
  const qc = useQueryClient();
  const { data: rules } = useQuery({ queryKey: ['alerts'], queryFn: alerts.list });
  const { data: firings } = useQuery({ queryKey: ['all-firings'], queryFn: () => alerts.allFirings(0, 50) });
  const { data: saved } = useQuery({ queryKey: ['saved-queries'], queryFn: savedQueries.list });
  const [editing, setEditing] = useState<any | null>(null);

  const save = useMutation({
    mutationFn: (r: any) => r.id ? alerts.update(r.id, r) : alerts.create(r),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['alerts'] }); setEditing(null); }
  });
  const runNow = useMutation({
    mutationFn: alerts.runNow,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['all-firings'] })
  });
  const del = useMutation({ mutationFn: alerts.del, onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }) });

  return (
    <div className="grid grid-cols-2 gap-4">
      <div className="space-y-4">
        <Tile title="rules"
              action={<button onClick={() => setEditing({ name: '', cron: '0 */5 * * * *', deltaMode: 'ANY_NEW', enabled: true,
                                                          deliveryChannelsJson: '[{"channel":"INBOX"}]' })}
                              className="text-xs bg-accent text-surface font-semibold px-2 py-1 rounded">new</button>}>
          {rules?.length ? (
            <div className="divide-y divide-border text-sm">
              {rules.map((r: any) => (
                <div key={r.id} className="py-2">
                  <div className="flex justify-between">
                    <div>
                      <span className={r.enabled ? '' : 'opacity-50'}>{r.name}</span>
                      <span className="text-xs text-muted ml-2">{r.cron} · {r.deltaMode}</span>
                    </div>
                    <div className="flex gap-1 text-xs">
                      <button className="text-accent" onClick={() => runNow.mutate(r.id)}>run</button>
                      <button className="text-accent" onClick={() => setEditing(r)}>edit</button>
                      <button className="text-crit" onClick={() => del.mutate(r.id)}>del</button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : <div className="text-muted text-sm">no rules</div>}
        </Tile>

        <Tile title="editor">
          {editing ? (
            <div className="space-y-2 text-sm">
              <input className="w-full bg-surface border border-border rounded px-2 py-1"
                     placeholder="name" value={editing.name}
                     onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
              <select className="w-full bg-surface border border-border rounded px-2 py-1"
                      value={editing.savedQueryId ?? ''}
                      onChange={(e) => setEditing({ ...editing, savedQueryId: Number(e.target.value) })}>
                <option value="">— saved query —</option>
                {saved?.map((q: any) => <option key={q.id} value={q.id}>{q.name}</option>)}
              </select>
              <input className="w-full bg-surface border border-border rounded px-2 py-1"
                     placeholder="cron" value={editing.cron}
                     onChange={(e) => setEditing({ ...editing, cron: e.target.value })} />
              <select className="w-full bg-surface border border-border rounded px-2 py-1"
                      value={editing.deltaMode}
                      onChange={(e) => setEditing({ ...editing, deltaMode: e.target.value })}>
                <option value="ANY_NEW">ANY_NEW</option>
                <option value="COUNT_THRESHOLD">COUNT_THRESHOLD</option>
                <option value="SCHEDULE_ONLY">SCHEDULE_ONLY</option>
              </select>
              <textarea className="w-full bg-surface border border-border rounded px-2 py-1 font-mono text-xs" rows={5}
                        placeholder='delivery channels JSON, e.g. [{"channel":"EMAIL","email":"you@x.com"},{"channel":"INBOX"}]'
                        value={editing.deliveryChannelsJson}
                        onChange={(e) => setEditing({ ...editing, deliveryChannelsJson: e.target.value })} />
              <label className="flex items-center gap-2">
                <input type="checkbox" checked={editing.enabled}
                       onChange={(e) => setEditing({ ...editing, enabled: e.target.checked })} />
                <span>enabled</span>
              </label>
              <div className="flex gap-2">
                <button className="bg-accent text-surface font-semibold px-3 py-1 rounded" onClick={() => save.mutate(editing)}>save</button>
                <button className="text-muted" onClick={() => setEditing(null)}>cancel</button>
              </div>
            </div>
          ) : <div className="text-muted text-sm">click new or edit a rule</div>}
        </Tile>
      </div>

      <Tile title="firings (all rules)">
        {firings?.length ? (
          <div className="divide-y divide-border text-sm">
            {firings.map((f: any) => (
              <div key={f.id} className="py-2">
                <div className="text-xs text-muted">{new Date(f.firedAt).toLocaleString()} · rule {f.alertRuleId}</div>
                <div className="text-xs mt-1 truncate">{f.resultSummaryJson}</div>
              </div>
            ))}
          </div>
        ) : <div className="text-muted text-sm">nothing has fired yet</div>}
      </Tile>
    </div>
  );
}
