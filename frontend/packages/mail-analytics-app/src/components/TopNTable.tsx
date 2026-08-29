import { Link } from 'react-router-dom';

interface Row { value: string; count: number; }
interface Props { rows?: Row[]; linkTo?: (v: string) => string; }
export default function TopNTable({ rows, linkTo }: Props) {
  if (!rows || rows.length === 0) return <div className="text-muted text-sm">no data</div>;
  const max = Math.max(...rows.map((r) => r.count));
  return (
    <div className="space-y-1 text-sm">
      {rows.map((r) => (
        <div key={r.value} className="flex items-center gap-2">
          <div className="flex-1 truncate">
            {linkTo ? <Link to={linkTo(r.value)} className="text-accent hover:underline">{r.value}</Link> : r.value}
          </div>
          <div className="w-32 bg-surface h-2 rounded overflow-hidden">
            <div className="bg-accent h-full" style={{ width: `${(r.count / max) * 100}%` }} />
          </div>
          <div className="w-12 text-right text-muted tabular-nums">{r.count}</div>
        </div>
      ))}
    </div>
  );
}
