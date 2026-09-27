import { useState } from 'react';

const BUSY_STATUSES = new Set(['PROCESSING', 'EXECUTING']);

export default function MessageInput({ status, onSend, onEndSession }) {
  const [value, setValue] = useState('');
  const [validationError, setValidationError] = useState(null);
  const disabled = BUSY_STATUSES.has(status);

  function submit() {
    if (disabled) return;
    if (!value.trim()) {
      setValidationError('Message cannot be empty.');
      return;
    }
    setValidationError(null);
    onSend(value.trim());
    setValue('');
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      submit();
    }
  }

  return (
    <div className="message-input">
      {validationError && (
        <div className="message-input-error" data-testid="message-input-error">
          {validationError}
        </div>
      )}
      <div className="message-input-row">
        <textarea
          data-testid="message-textarea"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={disabled ? 'Agent is working…' : 'Describe what you need (Enter to send, Shift+Enter for a new line)'}
          disabled={disabled}
          rows={3}
        />
        <div className="message-input-actions">
          <button type="button" data-testid="send-button" onClick={submit} disabled={disabled}>
            Send
          </button>
          <button type="button" data-testid="end-session-button" className="secondary" onClick={onEndSession}>
            End session
          </button>
        </div>
      </div>
    </div>
  );
}
