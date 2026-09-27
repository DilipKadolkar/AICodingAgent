import { useEffect, useRef, useState } from 'react';
import { getSession, postMessage, endSession, streamSession } from '../api/client.js';
import StatusBadge from '../components/StatusBadge.jsx';
import MessageList from '../components/MessageList.jsx';
import MessageInput from '../components/MessageInput.jsx';

const RESPONSE_TIMEOUT_MS = 90_000;

export default function ChatPage({ sessionId, onEnded, onBack }) {
  const [session, setSession] = useState(null);
  const [error, setError] = useState(null);
  const [lastFailedMessage, setLastFailedMessage] = useState(null);
  const [slow, setSlow] = useState(false);
  const timeoutRef = useRef(null);
  const closeStreamRef = useRef(null);

  function load() {
    getSession(sessionId)
      .then(setSession)
      .catch((err) => setError(err.message || 'Could not load this session.'));
  }

  useEffect(() => {
    load();
    return () => closeStreamRef.current?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  function startStream() {
    closeStreamRef.current?.();
    closeStreamRef.current = streamSession(sessionId, {
      onUpdate: (detail) => setSession(detail),
      onStatus: () => {},
      onError: () => {
        // Best-effort live updates; a final getSession() still runs after send() below.
      },
    });
  }

  async function handleSend(content) {
    setError(null);
    setLastFailedMessage(null);
    setSlow(false);
    startStream();
    timeoutRef.current = setTimeout(() => setSlow(true), RESPONSE_TIMEOUT_MS);
    try {
      const detail = await postMessage(sessionId, content);
      setSession(detail);
    } catch (err) {
      setError(err.message || 'The agent could not process that request.');
      setLastFailedMessage(content);
      load();
    } finally {
      clearTimeout(timeoutRef.current);
      setSlow(false);
    }
  }

  function handleRetry() {
    if (lastFailedMessage) {
      const msg = lastFailedMessage;
      setLastFailedMessage(null);
      handleSend(msg);
    }
  }

  async function handleEndSession() {
    try {
      await endSession(sessionId);
      onEnded(sessionId);
    } catch (err) {
      setError(err.message || 'Could not end the session.');
    }
  }

  if (!session) {
    return (
      <div className="page chat-page">
        {error ? <div className="form-error">{error}</div> : <p>Loading session…</p>}
        <button type="button" className="secondary" onClick={onBack}>Back</button>
      </div>
    );
  }

  return (
    <div className="page chat-page">
      <div className="chat-header">
        <button type="button" className="link-button" onClick={onBack}>&larr; Back</button>
        <h2>{session.title}</h2>
        <StatusBadge status={session.status} />
      </div>
      <div className="chat-repo-path">{session.repositoryPath}</div>

      {error && (
        <div className="form-error" data-testid="chat-error">
          {error}
          {lastFailedMessage && (
            <button type="button" className="link-button" onClick={handleRetry} data-testid="retry-button">
              Retry
            </button>
          )}
        </div>
      )}
      {slow && (
        <div className="form-warning" data-testid="slow-warning">
          This is taking unusually long. The agent may still be working, or the local model may be unresponsive.
        </div>
      )}

      <MessageList messages={session.messages} toolCalls={session.toolCalls} />
      <MessageInput status={session.status} onSend={handleSend} onEndSession={handleEndSession} />
    </div>
  );
}
