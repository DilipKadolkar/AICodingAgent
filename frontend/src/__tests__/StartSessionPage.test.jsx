import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import StartSessionPage from '../pages/StartSessionPage.jsx';
import * as api from '../api/client.js';

vi.mock('../api/client.js');

describe('StartSessionPage', () => {
  it('rejects an empty repository path without calling the API', () => {
    render(<StartSessionPage onSessionStarted={vi.fn()} onShowResume={vi.fn()} />);
    fireEvent.click(screen.getByTestId('start-session-submit'));
    expect(screen.getByTestId('start-session-error')).toBeInTheDocument();
    expect(api.createSession).not.toHaveBeenCalled();
  });

  it('navigates to the chat view on a successful submit', async () => {
    api.createSession.mockResolvedValue({ id: 'new-session-id' });
    const onSessionStarted = vi.fn();

    render(<StartSessionPage onSessionStarted={onSessionStarted} onShowResume={vi.fn()} />);
    fireEvent.change(screen.getByTestId('repository-path-input'), { target: { value: '/tmp/repo' } });
    fireEvent.click(screen.getByTestId('start-session-submit'));

    await waitFor(() => expect(onSessionStarted).toHaveBeenCalledWith('new-session-id'));
  });

  it('shows a friendly error when session creation fails', async () => {
    api.createSession.mockRejectedValue(new Error('repositoryPath does not exist'));

    render(<StartSessionPage onSessionStarted={vi.fn()} onShowResume={vi.fn()} />);
    fireEvent.change(screen.getByTestId('repository-path-input'), { target: { value: '/no/such/path' } });
    fireEvent.click(screen.getByTestId('start-session-submit'));

    await waitFor(() => expect(screen.getByTestId('start-session-error')).toHaveTextContent('does not exist'));
  });
});
