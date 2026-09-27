import { useState } from 'react';
import DiffView from './DiffView.jsx';

function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function summarizeInput(inputParams) {
  const parsed = parseJson(inputParams);
  if (!parsed) return '';
  const entries = Object.entries(parsed);
  if (entries.length === 0) return '';
  return entries.map(([k, v]) => `${k}: ${String(v).slice(0, 60)}`).join(', ');
}

export default function ToolCallCard({ toolCall }) {
  const [expanded, setExpanded] = useState(false);
  const output = parseJson(toolCall.outputResult);
  const isFailed = toolCall.status === 'FAILED';

  return (
    <div className={`tool-call-card ${isFailed ? 'tool-call-failed' : ''}`} data-testid="tool-call-card">
      <button
        type="button"
        className="tool-call-header"
        onClick={() => setExpanded((e) => !e)}
        aria-expanded={expanded}
      >
        <span className="tool-call-name">{toolCall.toolName}</span>
        <span className={`tool-call-status tool-call-status-${toolCall.status.toLowerCase()}`}>
          {toolCall.status}
        </span>
        <span className="tool-call-input-summary">{summarizeInput(toolCall.inputParams)}</span>
        <span className="tool-call-toggle">{expanded ? 'Hide details ▲' : 'Show details ▼'}</span>
      </button>
      {expanded && (
        <div className="tool-call-body" data-testid="tool-call-body">
          {isFailed && <p className="tool-call-error">{toolCall.errorMessage}</p>}
          {output?.diff && <DiffView diff={output.diff} />}
          {!output?.diff && output?.content && <pre className="tool-call-content">{output.content}</pre>}
          {!output?.diff && !output?.content && output && (
            <pre className="tool-call-content">{JSON.stringify(output, null, 2)}</pre>
          )}
        </div>
      )}
    </div>
  );
}
