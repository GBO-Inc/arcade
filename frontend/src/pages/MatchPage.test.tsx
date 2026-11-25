import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_TOKEN_KEY } from '../api/client';
import type {
  MatchFinalResult,
  MatchMoveEvent,
  MatchState,
} from '../games/matchTypes';
import { AuthProvider } from '../hooks/AuthContext';
import { SocketProvider } from '../hooks/SocketContext';
import { FakeStompClient } from '../realtime/fakeStompClient';
import { MatchPage } from './MatchPage';

const MATCH: MatchState = {
  matchId: 'm1',
  roomId: 42,
  gameType: 'TIC_TAC_TOE',
  status: 'IN_PROGRESS',
  players: ['7', '8'],
  currentTurnPlayerId: '7',
  state: {},
  moveCount: 0,
};

const PROFILES: Record<string, unknown> = {
  '7': { id: 7, displayName: 'Alice', avatarColour: '#3B82F6' },
  '8': { id: 8, displayName: 'Bob', avatarColour: '#EF4444' },
};

const TOPIC = '/topic/matches/m1';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Answers the match request with `matchResponse`; `undefined` never answers. */
function mockApi(matchResponse?: Response) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse({ id: 7, username: 'alice' }));
      }
      if (url.endsWith('/api/matches/m1')) {
        return matchResponse
          ? Promise.resolve(matchResponse)
          : new Promise(() => undefined);
      }
      const profile = PROFILES[url.split('/').pop() ?? ''];
      return Promise.resolve(
        profile ? jsonResponse(profile) : jsonResponse({}, 404),
      );
    }),
  );
}

function renderMatch() {
  render(
    <MemoryRouter initialEntries={['/match/m1']}>
      <AuthProvider>
        <SocketProvider createClient={FakeStompClient.create}>
          <Routes>
            <Route path="/match/:id" element={<MatchPage />} />
            <Route path="/lobby" element={<h1>Lobby page</h1>} />
          </Routes>
        </SocketProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

/**
 * Opens the page for a loaded match and connects the socket, as the server would. Waits for both
 * player profiles, which load separately, so name assertions do not race them.
 */
async function openMatch(match: MatchState = MATCH) {
  mockApi(jsonResponse(match));
  renderMatch();
  await screen.findByTestId('turn-indicator');
  await screen.findByText('Alice');
  await screen.findByText('Bob');
  act(() => FakeStompClient.latest.connect());
}

function deliver(destination: string, payload: unknown) {
  act(() => FakeStompClient.latest.deliver(destination, payload));
}

function moveEvent(match: Partial<MatchState>): MatchMoveEvent {
  return {
    match: { ...MATCH, ...match },
    move: { row: 0, col: 0 },
    movedBy: '7',
  };
}

function finalResult(result: Partial<MatchFinalResult>): MatchFinalResult {
  return {
    matchId: 'm1',
    roomId: 42,
    gameType: 'TIC_TAC_TOE',
    players: ['7', '8'],
    outcome: 'WIN',
    winnerId: '7',
    reason: 'Game completed',
    ...result,
  };
}

describe('MatchPage', () => {
  beforeEach(() => {
    FakeStompClient.reset();
    localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-token');
  });

  describe('states', () => {
    it('shows a loading state until the match has loaded', async () => {
      mockApi();
      renderMatch();

      expect(await screen.findByText('Loading match…')).toBeVisible();
      expect(screen.queryByTestId('turn-indicator')).toBeNull();
    });

    it('shows "match not found" for an unknown match', async () => {
      mockApi(jsonResponse({ message: 'Match not found' }, 404));
      renderMatch();

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Match not found',
      );
    });

    it('shows an error to a player who is not in the match', async () => {
      mockApi(
        jsonResponse({ message: 'You are not a player in this match' }, 403),
      );
      renderMatch();

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'You are not a player in this match',
      );
      expect(screen.queryByTestId('turn-indicator')).toBeNull();
      expect(FakeStompClient.latest.subscriptions.has(TOPIC)).toBe(false);
    });

    it('shows an error when the match cannot be loaded', async () => {
      mockApi(jsonResponse({ message: 'Unexpected error' }, 500));
      renderMatch();

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Could not load the match: Unexpected error',
      );
    });
  });

  describe('player panels', () => {
    it('shows both players with their colour and marks the current user', async () => {
      await openMatch();

      const alice = await screen.findByRole('group', { name: 'Alice' });
      const bob = screen.getByRole('group', { name: 'Bob' });
      expect(alice).toHaveTextContent('you');
      expect(alice).toHaveTextContent('Blue');
      expect(bob).toHaveTextContent('Red');
      expect(bob).not.toHaveTextContent('you');
    });

    it('subscribes to the match topic and renders the board slot', async () => {
      await openMatch();

      expect(FakeStompClient.latest.subscriptions.has(TOPIC)).toBe(true);
      expect(
        screen.getByText('Board for Tic-tac-toe coming soon'),
      ).toBeVisible();
    });
  });

  describe('turn indicator', () => {
    it('says it is my turn when the server says so', async () => {
      await openMatch();

      expect(screen.getByTestId('turn-indicator')).toHaveTextContent(
        'Your turn',
      );
    });

    it("shows the opponent's name when it is their turn", async () => {
      await openMatch({ ...MATCH, currentTurnPlayerId: '8' });

      expect(screen.getByTestId('turn-indicator')).toHaveTextContent(
        "Bob's turn",
      );
    });

    it('changes when the server confirms a move', async () => {
      await openMatch();

      deliver(TOPIC, moveEvent({ currentTurnPlayerId: '8', moveCount: 1 }));
      expect(screen.getByTestId('turn-indicator')).toHaveTextContent(
        "Bob's turn",
      );

      deliver(TOPIC, moveEvent({ currentTurnPlayerId: '7', moveCount: 2 }));
      expect(screen.getByTestId('turn-indicator')).toHaveTextContent(
        'Your turn',
      );
    });

    it('takes the state the server sends on subscribing', async () => {
      await openMatch();

      deliver(TOPIC, { ...MATCH, currentTurnPlayerId: '8', moveCount: 1 });

      expect(screen.getByTestId('turn-indicator')).toHaveTextContent(
        "Bob's turn",
      );
    });

    it('ignores a state older than the one shown', async () => {
      await openMatch();
      deliver(TOPIC, moveEvent({ currentTurnPlayerId: '8', moveCount: 3 }));

      deliver(TOPIC, { ...MATCH, currentTurnPlayerId: '7', moveCount: 2 });

      expect(screen.getByTestId('turn-indicator')).toHaveTextContent(
        "Bob's turn",
      );
    });
  });

  describe('result dialog', () => {
    it('is not shown while the match is running', async () => {
      await openMatch();

      expect(screen.queryByRole('dialog')).toBeNull();
    });

    it('tells the winner they won', async () => {
      await openMatch();

      deliver(TOPIC, finalResult({ winnerId: '7' }));

      expect(screen.getByRole('dialog', { name: 'You won' })).toBeVisible();
    });

    it('tells the loser they lost', async () => {
      await openMatch();

      deliver(TOPIC, finalResult({ winnerId: '8' }));

      expect(screen.getByRole('dialog', { name: 'You lost' })).toBeVisible();
    });

    it('shows a draw', async () => {
      await openMatch();

      deliver(TOPIC, finalResult({ outcome: 'DRAW', winnerId: null }));

      expect(screen.getByRole('dialog', { name: 'Draw' })).toBeVisible();
    });

    it('shows an abandoned match with the reason', async () => {
      await openMatch();

      deliver(
        TOPIC,
        finalResult({
          outcome: 'ABANDONED',
          winnerId: null,
          reason: 'All players disconnected',
        }),
      );

      const dialog = screen.getByRole('dialog', { name: 'Match abandoned' });
      expect(dialog).toHaveTextContent('All players disconnected');
    });

    it('appears after the last move event without moving the turn indicator', async () => {
      await openMatch();

      deliver(
        TOPIC,
        moveEvent({ status: 'FINISHED', currentTurnPlayerId: null }),
      );
      deliver(TOPIC, finalResult({ winnerId: '7' }));

      expect(screen.getByRole('dialog', { name: 'You won' })).toBeVisible();
      expect(screen.getByTestId('turn-indicator')).toHaveTextContent(
        'Match over',
      );
    });

    it('leads back to the lobby', async () => {
      await openMatch();
      deliver(TOPIC, finalResult({ winnerId: '7' }));

      fireEvent.click(screen.getByRole('button', { name: 'Back to lobby' }));

      expect(
        await screen.findByRole('heading', { name: 'Lobby page' }),
      ).toBeVisible();
    });
  });

  describe('server errors', () => {
    it('shows an error for this match', async () => {
      await openMatch();

      deliver('/user/queue/errors', {
        code: 'NOT_YOUR_TURN',
        message: 'Not your turn',
        matchId: 'm1',
      });

      expect(screen.getByRole('alert')).toHaveTextContent('Not your turn');
    });

    it('ignores errors for other matches', async () => {
      await openMatch();

      deliver('/user/queue/errors', {
        code: 'NOT_YOUR_TURN',
        message: 'Not your turn',
        matchId: 'other',
      });

      expect(screen.queryByRole('alert')).toBeNull();
    });
  });
});
