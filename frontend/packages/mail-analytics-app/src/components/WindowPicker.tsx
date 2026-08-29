import { useAnalyticsStore } from '../state/store';

const OPTIONS: Array<{ label: string; mode: '24h' | '7d' | '30d' | '90d' }> = [
  { label: '24h', mode: '24h' },
  { label: '7d', mode: '7d' },
  { label: '30d', mode: '30d' },
  { label: '90d', mode: '90d' }
];

export default function WindowPicker() {
  const { windowMode, setWindow } = useAnalyticsStore();
  return (
    <div className="flex gap-1 bg-panel rounded border border-border p-1">
      {OPTIONS.map((o) => (
        <button
          key={o.mode}
          onClick={() => setWindow(o.mode)}
          className={`px-2 py-1 rounded text-xs ${windowMode === o.mode ? 'bg-accent text-surface font-semibold' : 'text-muted'}`}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
