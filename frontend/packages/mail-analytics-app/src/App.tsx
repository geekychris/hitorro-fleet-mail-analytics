import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { inbox } from './api/client';
import Dashboard from './pages/Dashboard';
import Search from './pages/Search';
import SenderView from './pages/SenderView';
import DomainView from './pages/DomainView';
import TopicView from './pages/TopicView';
import ThreadsView from './pages/ThreadsView';
import SavedQueries from './pages/SavedQueries';
import Alerts from './pages/Alerts';
import Inbox from './pages/Inbox';
import Reports from './pages/Reports';
import Suggestions from './pages/Suggestions';
import Settings from './pages/Settings';
import WindowPicker from './components/WindowPicker';

export default function App() {
  const { data: unread } = useQuery({
    queryKey: ['inbox-unread'],
    queryFn: inbox.unreadCount,
    refetchInterval: 60_000
  });

  return (
    <div className="flex h-full">
      <nav className="w-56 bg-panel border-r border-border p-4 flex flex-col gap-1">
        <div className="text-accent font-bold text-lg mb-4">mail analytics</div>
        <NavItem to="/">Dashboard</NavItem>
        <NavItem to="/search">Search</NavItem>
        <NavItem to="/topics">Topics</NavItem>
        <NavItem to="/threads">Threads</NavItem>
        <NavItem to="/saved">Saved queries</NavItem>
        <NavItem to="/alerts">Alerts</NavItem>
        <NavItem to="/inbox">Inbox {unread && unread.count > 0 ? `(${unread.count})` : ''}</NavItem>
        <NavItem to="/reports">Reports</NavItem>
        <NavItem to="/suggestions">Suggestions</NavItem>
        <div className="mt-auto pt-4 border-t border-border">
          <NavItem to="/settings">Settings</NavItem>
        </div>
      </nav>
      <main className="flex-1 overflow-auto p-6">
        <div className="mb-4 flex justify-between items-center">
          <div className="text-sm text-muted">hitorro-fleet-mail-analytics</div>
          <WindowPicker />
        </div>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/search" element={<Search />} />
          <Route path="/senders/:email" element={<SenderView />} />
          <Route path="/domains/:domain" element={<DomainView />} />
          <Route path="/topics" element={<TopicView />} />
          <Route path="/threads" element={<ThreadsView />} />
          <Route path="/saved" element={<SavedQueries />} />
          <Route path="/alerts" element={<Alerts />} />
          <Route path="/inbox" element={<Inbox />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/suggestions" element={<Suggestions />} />
          <Route path="/settings" element={<Settings />} />
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </main>
    </div>
  );
}

function NavItem({ to, children }: { to: string; children: React.ReactNode }) {
  return (
    <NavLink
      to={to}
      end={to === '/'}
      className={({ isActive }) =>
        `px-3 py-2 rounded text-sm ${isActive ? 'bg-accent text-surface font-semibold' : 'text-muted hover:bg-surface hover:text-text'}`
      }
    >
      {children}
    </NavLink>
  );
}
