import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { inbox } from '../api/client';
import Tile from '../components/Tile';

export default function Inbox() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['inbox'], queryFn: () => inbox.list(0, 200) });
  const invalidate = () => qc.invalidateQueries({ queryKey: ['inbox'] });
  const read = useMutation({ mutationFn: inbox.read, onSuccess: invalidate });
  const dismiss = useMutation({ mutationFn: inbox.dismiss, onSuccess: invalidate });
  const snooze = useMutation({ mutationFn: (id: number) => inbox.snooze(id, 60), onSuccess: invalidate });

  return (
    <Tile title={`inbox (${data?.length ?? 0})`}>
      {data?.length ? (
        <div className="divide-y divide-border">
          {data.map((i: any) => (
            <div key={i.id} className={`py-3 ${i.read ? 'opacity-60' : ''}`}>
              <div className="flex justify-between items-baseline">
                <div>
                  <span className={`text-xs font-bold ${i.severity === 'CRIT' ? 'text-crit' : i.severity === 'WARN' ? 'text-warn' : 'text-accent'} mr-2`}>{i.severity}</span>
                  <span className="font-semibold">{i.title}</span>
                </div>
                <div className="text-xs text-muted">{new Date(i.createdAt).toLocaleString()}</div>
              </div>
              {i.bodyPreview && <div className="text-sm text-muted mt-1">{i.bodyPreview}</div>}
              <div className="flex gap-2 text-xs mt-2">
                {!i.read && <button className="text-accent" onClick={() => read.mutate(i.id)}>mark read</button>}
                <button className="text-accent" onClick={() => snooze.mutate(i.id)}>snooze 1h</button>
                <button className="text-crit" onClick={() => dismiss.mutate(i.id)}>dismiss</button>
              </div>
            </div>
          ))}
        </div>
      ) : <div className="text-muted text-sm">inbox empty</div>}
    </Tile>
  );
}
