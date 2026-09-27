import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import MessageList from '../components/MessageList.jsx';

describe('MessageList', () => {
  it('shows an empty-state hint when there are no messages or tool calls', () => {
    render(<MessageList messages={[]} toolCalls={[]} />);
    expect(screen.getByText(/no messages yet/i)).toBeInTheDocument();
  });

  it('renders messages and tool calls interleaved in chronological order', () => {
    const messages = [
      { id: 'm1', role: 'USER', content: 'hello', createdAt: '2024-01-01T00:00:00Z' },
      { id: 'm2', role: 'AGENT', content: 'final answer', createdAt: '2024-01-01T00:00:10Z' },
    ];
    const toolCalls = [
      {
        id: 't1', toolName: 'LIST_FILES', status: 'SUCCEEDED',
        inputParams: '{}', outputResult: '{}', startedAt: '2024-01-01T00:00:05Z',
      },
    ];
    render(<MessageList messages={messages} toolCalls={toolCalls} />);

    const bubbles = screen.getAllByTestId('message-bubble');
    const toolCards = screen.getAllByTestId('tool-call-card');
    expect(bubbles).toHaveLength(2);
    expect(toolCards).toHaveLength(1);
  });
});
