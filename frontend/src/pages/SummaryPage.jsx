import { useEffect, useState } from 'react';
import { getSummary } from '../api/client.js';

function formatDuration(ms) {
  if (ms == null) return '—';
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s`;
}

export default function SummaryPage({ sessionId, onBack }) {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    getSummary(sessionId)
      .then(setSummary)
      .catch((err) => setError(err.message || 'Could not load the summary.'));
  }, [sessionId]);

  if (error) {
    return (
      <div className="page summary-page">
        <div className="form-error">{error}</div>
        <button type="button" className="secondary" onClick={onBack}>Back</button>
      </div>
    );
  }
  if (!summary) {
    return <div className="page summary-page"><p>Generating summary…</p></div>;
  }

  const { narrative, narrativeAvailable, filesInspected, filesModified, commandsExecuted,
    verificationResults, processingSummary } = summary;

  return (
    <div className="page summary-page">
      <div className="page-header">
        <h2>Session summary</h2>
        <button type="button" className="secondary" onClick={onBack}>Back to sessions</button>
      </div>

      <section className="summary-section">
        <h3>What happened</h3>
        <p className={narrativeAvailable ? '' : 'narrative-unavailable'}>{narrative}</p>
      </section>

      <section className="summary-section">
        <h3>Files inspected</h3>
        {filesInspected.length === 0 ? (
          <p className="empty-hint">No files were inspected in this session.</p>
        ) : (
          <ul>{filesInspected.map((f) => <li key={f}>{f}</li>)}</ul>
        )}
      </section>

      <section className="summary-section">
        <h3>Files modified</h3>
        {filesModified.length === 0 ? (
          <p className="empty-hint">No files were created or modified in this session.</p>
        ) : (
          <ul>
            {filesModified.map((f) => (
              <li key={f.path}>
                <strong>{f.changeType}</strong> — {f.path}
                {f.backupPath && <span className="backup-note"> (backup saved)</span>}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="summary-section">
        <h3>Commands executed</h3>
        {commandsExecuted.length === 0 ? (
          <p className="empty-hint">No commands were executed in this session.</p>
        ) : (
          <ul>
            {commandsExecuted.map((c, idx) => (
              <li key={idx}>
                <code>{c.command}</code> — exit {c.exitCode}
                {c.verification ? ' (verification)' : ''}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="summary-section">
        <h3>Verification results</h3>
        {verificationResults.length === 0 ? (
          <p className="empty-hint">No verification/test runs in this session.</p>
        ) : (
          <ul>
            {verificationResults.map((v, idx) => (
              <li key={idx} className={v.passed ? 'verification-passed' : 'verification-failed'}>
                {v.passed ? '✅ PASSED' : '❌ FAILED'} — <code>{v.command}</code> (exit {v.exitCode})
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="summary-section">
        <h3>Processing summary</h3>
        <ul className="processing-summary-list">
          <li>Duration: {formatDuration(processingSummary.durationMillis)}</li>
          <li>User turns: {processingSummary.turnCount}</li>
          <li>Final status: {processingSummary.finalStatus}</li>
          <li>
            Tool calls:{' '}
            {Object.entries(processingSummary.toolCallCountsByType).map(([k, v]) => `${k}: ${v}`).join(', ') || 'none'}
          </li>
        </ul>
      </section>
    </div>
  );
}
