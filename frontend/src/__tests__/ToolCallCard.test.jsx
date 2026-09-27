import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import ToolCallCard from '../components/ToolCallCard.jsx';

const toolCall = {
  id: 't1',
  toolName: 'READ_FILE',
  status: 'SUCCEEDED',
  inputParams: JSON.stringify({ path: 'src/Main.java' }),
  outputResult: JSON.stringify({ path: 'src/Main.java', content: 'a'.repeat(500) }),
  startedAt: new Date().toISOString(),
};

describe('ToolCallCard', () => {
  it('stays collapsed by default even for a long result', () => {
    render(<ToolCallCard toolCall={toolCall} />);
    expect(screen.queryByTestId('tool-call-body')).not.toBeInTheDocument();
  });

  it('expands to show details on click', () => {
    render(<ToolCallCard toolCall={toolCall} />);
    fireEvent.click(screen.getByTestId('tool-call-card').querySelector('button'));
    expect(screen.getByTestId('tool-call-body')).toBeInTheDocument();
    expect(screen.getByTestId('tool-call-body')).toHaveTextContent('aaaa');
  });

  it('shows the error message for a failed tool call', () => {
    const failed = { ...toolCall, status: 'FAILED', errorMessage: 'File not found', outputResult: null };
    render(<ToolCallCard toolCall={failed} />);
    fireEvent.click(screen.getByTestId('tool-call-card').querySelector('button'));
    expect(screen.getByTestId('tool-call-body')).toHaveTextContent('File not found');
  });
});
