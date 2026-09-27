import { useEffect, useState } from 'react';
import { listSessions } from '../api/client.js';
import StatusBadge from '../components/StatusBadge.jsx';

export default function ResumeSessionsPage({ onResume, onShowStart }) {
  const [sessions, setSessions] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    listSessions()
      .then((data) => {
        if (!cancelled) setSessions(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || 'Could not load sessions.');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="page resume-sessions-page">
      <div className="page-header">
        <h2>Resume a session</h2>
        <button type="button" className="secondary" onClick={onShowStart}>
          Start a new session
        </button>
      </div>

      {error && <div className="form-error" data-testid="resume-list-error">{error}</div>}
      {!error && sessions === null && <p>Loading sessions…</p>}
      {!error && sessions !== null && sessions.length === 0 && (
        <p className="empty-hint">No sessions yet. Start your first one above.</p>
      )}
      {sessions && sessions.length > 0 && (
        <ul className="session-list" data-testid="session-list">
          {sessions.map((s) => (
            <li key={s.id} className="session-list-item" onClick={() => onResume(s.id)} data-testid="session-list-item">
              <div className="session-list-title">{s.title}</div>
              <div className="session-list-meta">
                <StatusBadge status={s.status} />
                <span className="session-list-updated">
                  updated {new Date(s.updatedAt).toLocaleString()}
                </span>
              </div>
              <div className="session-list-path">{s.repositoryPath}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
