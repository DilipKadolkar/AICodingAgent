import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusBadge from '../components/StatusBadge.jsx';

describe('StatusBadge', () => {
  it.each([
    ['IDLE', 'Idle'],
    ['PROCESSING', 'Processing'],
    ['EXECUTING', 'Executing'],
    ['COMPLETED', 'Completed'],
    ['FAILED', 'Failed'],
  ])('renders a distinct badge for status %s', (status, label) => {
    render(<StatusBadge status={status} />);
    const badge = screen.getByTestId('status-badge');
    expect(badge).toHaveTextContent(label);
    expect(badge.className).toContain(`status-${status.toLowerCase()}`);
  });
});
