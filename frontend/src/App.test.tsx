import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, it, expect, vi } from 'vitest';
import App, { AppRoutes } from './App';
import { ACCESS_TOKEN_KEY } from './api/client';
import { AuthProvider } from './hooks/AuthContext';

const IN_PROGRESS_MATCH = {
  matchId: 'room-42',
  roomId: 42,
  gameType: 'TIC_TAC_TOE',
  status: 'IN_PROGRESS',
  players: ['1', '8'],
  currentTurnPlayerId: '1',
  state: {},
  moveCount: 0,
};

function renderRoutes(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('App routing', () => {
  beforeEach(() => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((input: RequestInfo | URL) => {
        const url = String(input);
        const body = url.endsWith('/api/matches/room-42')
          ? IN_PROGRESS_MATCH
          : url.endsWith('/api/rooms')
            ? []
            : { id: 1 };
        return Promise.resolve(
          new Response(JSON.stringify(body), {
            headers: { 'Content-Type': 'application/json' },
          }),
        );
      }),
    );
  });

  it('navigates between pages without reload', async () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'test-token');
    render(<App />);

    expect(
      await screen.findByRole('heading', { name: 'Lobby' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'Leaderboard' }));
    expect(
      await screen.findByRole('heading', { name: 'Leaderboard' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'History' }));
    expect(
      await screen.findByRole('heading', { name: 'History' }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('link', { name: 'Register' }));
    expect(
      screen.getByRole('heading', { name: 'Register' }),
    ).toBeInTheDocument();
  });

  it('loads a URL directly', async () => {
    localStorage.setItem(ACCESS_TOKEN_KEY, 'test-token');
    renderRoutes('/match/room-42');

    expect(await screen.findByTestId('turn-indicator')).toHaveTextContent(
      'Your turn',
    );
  });

  it('loads the UI demo directly', () => {
    renderRoutes('/ui-demo');

    expect(screen.getByRole('heading', { name: 'UI kit' })).toBeInTheDocument();
  });

  it('shows 404 on unknown route', () => {
    renderRoutes('/does-not-exist');

    expect(screen.getByText(/404/i)).toBeInTheDocument();
  });
});
