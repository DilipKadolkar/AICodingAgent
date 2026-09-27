import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ResumeSessionsPage from '../pages/ResumeSessionsPage.jsx';
import * as api from '../api/client.js';

vi.mock('../api/client.js');

describe('ResumeSessionsPage', () => {
  it('renders sessions returned by the API and navigates on click', async () => {
    api.listSessions.mockResolvedValue([
      { id: 's1', title: 'Fix bug', status: 'COMPLETED', updatedAt: '2024-01-01T00:00:00Z', repositoryPath: '/repo' },
    ]);
    const onResume = vi.fn();

    render(<ResumeSessionsPage onResume={onResume} onShowStart={vi.fn()} />);

    await waitFor(() => expect(screen.getByTestId('session-list')).toBeInTheDocument());
    fireEvent.click(screen.getByTestId('session-list-item'));
    expect(onResume).toHaveBeenCalledWith('s1');
  });

  it('shows a friendly message, not a raw error, when the API call fails', async () => {
    api.listSessions.mockRejectedValue(new Error('Backend unreachable'));

    render(<ResumeSessionsPage onResume={vi.fn()} onShowStart={vi.fn()} />);

    await waitFor(() => expect(screen.getByTestId('resume-list-error')).toBeInTheDocument());
    expect(screen.getByTestId('resume-list-error')).toHaveTextContent('Backend unreachable');
  });

  it('shows an empty-state hint when there are no sessions', async () => {
    api.listSessions.mockResolvedValue([]);
    render(<ResumeSessionsPage onResume={vi.fn()} onShowStart={vi.fn()} />);
    await waitFor(() => expect(screen.getByText(/no sessions yet/i)).toBeInTheDocument());
  });
});
