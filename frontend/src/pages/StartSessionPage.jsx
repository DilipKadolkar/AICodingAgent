import { useState } from 'react';
import { createSession } from '../api/client.js';

export default function StartSessionPage({ onSessionStarted, onShowResume }) {
  const [repositoryPath, setRepositoryPath] = useState('');
  const [title, setTitle] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (!repositoryPath.trim()) {
      setError('Repository path is required.');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const session = await createSession(repositoryPath.trim(), title.trim());
      onSessionStarted(session.id);
    } catch (err) {
      setError(err.message || 'Could not start a session. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page start-session-page">
      <h2>Start a new coding assistant session</h2>
      <form onSubmit={handleSubmit} className="start-session-form">
        <label>
          Repository path
          <input
            data-testid="repository-path-input"
            type="text"
            value={repositoryPath}
            onChange={(e) => setRepositoryPath(e.target.value)}
            placeholder="/absolute/path/to/your/project"
          />
        </label>
        <label>
          Title (optional)
          <input
            data-testid="title-input"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Fix the login bug"
          />
        </label>
        {error && (
          <div className="form-error" data-testid="start-session-error">
            {error}
          </div>
        )}
        <div className="form-actions">
          <button type="submit" disabled={loading} data-testid="start-session-submit">
            {loading ? 'Starting…' : 'Start session'}
          </button>
          <button type="button" className="secondary" onClick={onShowResume}>
            Resume a previous session
          </button>
        </div>
      </form>
    </div>
  );
}
