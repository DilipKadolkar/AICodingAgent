import { useEffect, useState } from 'react';
import { checkOllamaHealth } from './api/client.js';
import StartSessionPage from './pages/StartSessionPage.jsx';
import ResumeSessionsPage from './pages/ResumeSessionsPage.jsx';
import ChatPage from './pages/ChatPage.jsx';
import SummaryPage from './pages/SummaryPage.jsx';

export default function App() {
  const [view, setView] = useState({ name: 'start' });
  const [ollamaWarning, setOllamaWarning] = useState(null);

  useEffect(() => {
    checkOllamaHealth()
      .then((status) => {
        if (!status.reachable) {
          setOllamaWarning('Local AI model is not reachable. Start Ollama and try again.');
        } else if (!status.modelAvailable) {
          setOllamaWarning(status.detail);
        }
      })
      .catch(() => setOllamaWarning('Could not check the local AI model status.'));
  }, []);

  return (
    <div className="app">
      <header className="app-header">
        <h1>AI Coding Agent</h1>
      </header>

      {ollamaWarning && (
        <div className="ollama-warning" data-testid="ollama-warning">
          ⚠ {ollamaWarning}
        </div>
      )}

      <main className="app-main">
        {view.name === 'start' && (
          <StartSessionPage
            onSessionStarted={(id) => setView({ name: 'chat', sessionId: id })}
            onShowResume={() => setView({ name: 'resume' })}
          />
        )}
        {view.name === 'resume' && (
          <ResumeSessionsPage
            onResume={(id) => setView({ name: 'chat', sessionId: id })}
            onShowStart={() => setView({ name: 'start' })}
          />
        )}
        {view.name === 'chat' && (
          <ChatPage
            sessionId={view.sessionId}
            onEnded={(id) => setView({ name: 'summary', sessionId: id })}
            onBack={() => setView({ name: 'resume' })}
          />
        )}
        {view.name === 'summary' && (
          <SummaryPage sessionId={view.sessionId} onBack={() => setView({ name: 'resume' })} />
        )}
      </main>
    </div>
  );
}
