import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import MessageInput from '../components/MessageInput.jsx';

describe('MessageInput', () => {
  it('rejects submission of an empty message and does not call onSend', () => {
    const onSend = vi.fn();
    render(<MessageInput status="IDLE" onSend={onSend} onEndSession={vi.fn()} />);

    fireEvent.click(screen.getByTestId('send-button'));

    expect(onSend).not.toHaveBeenCalled();
    expect(screen.getByTestId('message-input-error')).toHaveTextContent('cannot be empty');
  });

  it('rejects a whitespace-only message', () => {
    const onSend = vi.fn();
    render(<MessageInput status="IDLE" onSend={onSend} onEndSession={vi.fn()} />);

    fireEvent.change(screen.getByTestId('message-textarea'), { target: { value: '   ' } });
    fireEvent.click(screen.getByTestId('send-button'));

    expect(onSend).not.toHaveBeenCalled();
  });

  it('submits on Enter and clears the input', () => {
    const onSend = vi.fn();
    render(<MessageInput status="IDLE" onSend={onSend} onEndSession={vi.fn()} />);

    const textarea = screen.getByTestId('message-textarea');
    fireEvent.change(textarea, { target: { value: 'hello agent' } });
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: false });

    expect(onSend).toHaveBeenCalledWith('hello agent');
    expect(textarea.value).toBe('');
  });

  it('does not submit on Shift+Enter, allowing a newline instead', () => {
    const onSend = vi.fn();
    render(<MessageInput status="IDLE" onSend={onSend} onEndSession={vi.fn()} />);

    const textarea = screen.getByTestId('message-textarea');
    fireEvent.change(textarea, { target: { value: 'line one' } });
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: true });

    expect(onSend).not.toHaveBeenCalled();
  });

  it('disables the input while the session is PROCESSING', () => {
    render(<MessageInput status="PROCESSING" onSend={vi.fn()} onEndSession={vi.fn()} />);
    expect(screen.getByTestId('message-textarea')).toBeDisabled();
    expect(screen.getByTestId('send-button')).toBeDisabled();
  });

  it('disables the input while the session is EXECUTING', () => {
    render(<MessageInput status="EXECUTING" onSend={vi.fn()} onEndSession={vi.fn()} />);
    expect(screen.getByTestId('message-textarea')).toBeDisabled();
  });

  it('re-enables the input once the session is IDLE again', () => {
    render(<MessageInput status="IDLE" onSend={vi.fn()} onEndSession={vi.fn()} />);
    expect(screen.getByTestId('message-textarea')).not.toBeDisabled();
  });

  it('calls onEndSession when the end-session button is clicked', () => {
    const onEndSession = vi.fn();
    render(<MessageInput status="IDLE" onSend={vi.fn()} onEndSession={onEndSession} />);
    fireEvent.click(screen.getByTestId('end-session-button'));
    expect(onEndSession).toHaveBeenCalled();
  });
});
