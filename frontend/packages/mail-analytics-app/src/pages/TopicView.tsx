import { useQuery } from '@tanstack/react-query';
import { topics } from '../api/client';
import { useAnalyticsStore } from '../state/store';
import Tile from '../components/Tile';
import TopNTable from '../components/TopNTable';

export default function TopicView() {
  const range = useAnalyticsStore((s) => s.effectiveRange());
  const { data } = useQuery({
    queryKey: ['topics', range],
    queryFn: () => topics.entities(range.from, range.to, 25)
  });
  return (
    <div className="grid grid-cols-2 gap-4">
      <Tile title="persons"><TopNTable rows={data?.persons} /></Tile>
      <Tile title="organizations"><TopNTable rows={data?.organizations} /></Tile>
      <Tile title="locations"><TopNTable rows={data?.locations} /></Tile>
      <Tile title="dates"><TopNTable rows={data?.dates} /></Tile>
    </div>
  );
}
