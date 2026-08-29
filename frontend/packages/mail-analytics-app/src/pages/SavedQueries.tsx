import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { savedQueries } from '../api/client';
import Tile from '../components/Tile';

export default function SavedQueries() {
  const qc = useQueryClient();
  const { data: list } = useQuery({ queryKey: ['saved-queries'], queryFn: savedQueries.list });
  const [editing, setEditing] = useState<any | null>(null);

  const save = useMutation({
    mutationFn: (q: any) => q.id ? savedQueries.update(q.id, q) : savedQueries.create(q),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['saved-queries'] }); setEditing(null); }
  });
  const del = useMutation({
    mutationFn: savedQueries.del,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['saved-queries'] })
  });

  return (
    <div className="grid grid-cols-2 gap-4">
      <Tile
        title="saved queries"
        action={<button onClick={() => setEditing({ name: '', description: '', dslJson: '{}', tags: '' })}
                        className="text-xs bg-accent text-surface font-semibold px-2 py-1 rounded">new</button>}
      >
        {list?.length ? (
          <div className="divide-y divide-border text-sm">
            {list.map((q) => (
              <div key={q.id} className="py-2 flex justify-between items-center">
                <div>
                  <div className="font-semibold">{q.name}</div>
                  <div className="text-xs text-muted">{q.description}</div>
                </div>
                <div className="flex gap-1">
                  <button className="text-xs text-accent" onClick={() => setEditing(q)}>edit</button>
                  <button className="text-xs text-crit" onClick={() => del.mutate(q.id)}>del</button>
                </div>
              </div>
            ))}
          </div>
        ) : <div className="text-muted text-sm">no saved queries</div>}
      </Tile>

      <Tile title={editing ? (editing.id ? `edit #${editing.id}` : 'new saved query') : 'editor'}>
        {editing ? (
          <div className="space-y-2 text-sm">
            <input className="w-full bg-surface border border-border rounded px-2 py-1"
                   placeholder="name" value={editing.name}
                   onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
            <input className="w-full bg-surface border border-border rounded px-2 py-1"
                   placeholder="description" value={editing.description ?? ''}
                   onChange={(e) => setEditing({ ...editing, description: e.target.value })} />
            <textarea className="w-full bg-surface border border-border rounded px-2 py-1 font-mono text-xs" rows={12}
                      placeholder='{"index":"mail","text":"...","filters":{"sender_domain":"..."},"facets":["sender_domain"],"offset":0,"limit":50}'
                      value={editing.dslJson ?? '{}'}
                      onChange={(e) => setEditing({ ...editing, dslJson: e.target.value })} />
            <input className="w-full bg-surface border border-border rounded px-2 py-1"
                   placeholder="tags (comma-separated)" value={editing.tags ?? ''}
                   onChange={(e) => setEditing({ ...editing, tags: e.target.value })} />
            <div className="flex gap-2">
              <button className="bg-accent text-surface font-semibold px-3 py-1 rounded" onClick={() => save.mutate(editing)}>save</button>
              <button className="text-muted" onClick={() => setEditing(null)}>cancel</button>
            </div>
          </div>
        ) : (
          <div className="text-muted text-sm">pick a query on the left, or click "new"</div>
        )}
      </Tile>
    </div>
  );
}
