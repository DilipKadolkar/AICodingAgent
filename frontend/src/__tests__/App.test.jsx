import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import * as api from '../api/client.js';

vi.mock('../api/client.js');

describe('App', () => {
  it('shows a warning banner when Ollama is unreachable at load', async () => {
    api.checkOllamaHealth.mockResolvedValue({ reachable: false, modelAvailable: false });
    render(<App />);
    await waitFor(() => expect(screen.getByTestId('ollama-warning')).toBeInTheDocument());
    expect(screen.getByTestId('ollama-warning')).toHaveTextContent('not reachable');
  });

  it('shows no warning banner when Ollama is reachable and the model is available', async () => {
    api.checkOllamaHealth.mockResolvedValue({ reachable: true, modelAvailable: true });
    render(<App />);
    await waitFor(() => expect(api.checkOllamaHealth).toHaveBeenCalled());
    expect(screen.queryByTestId('ollama-warning')).not.toBeInTheDocument();
  });
});
