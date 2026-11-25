import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_TOKEN_KEY } from '../api/client';
import { ConnectionStatus } from '../components/layout/ConnectionStatus';
import { FakeStompClient } from '../realtime/fakeStompClient';
import { AuthProvider } from './AuthContext';
import { SocketProvider } from './SocketContext';
import { useSocket } from './socket';

function Probe() {
  const { subscribe, send } = useSocket();
  const location = useLocation();
  return (
    <>
      <span data-testid="path">{location.pathname}</span>
      <button onClick={() => send('/app/match/1/move', { playerId: 'p1' })}>
        Send move
      </button>
      <button
        onClick={() =>
          subscribe('/topic/matches/1', (body) => {
            document.title = JSON.stringify(body);
          })
        }
      >
        Subscribe
      </button>
    </>
  );
}

async function renderApp() {
  render(
    <MemoryRouter initialEntries={['/lobby']}>
      <AuthProvider>
        <SocketProvider createClient={FakeStompClient.create}>
          <ConnectionStatus />
          <Probe />
        </SocketProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
  await screen.findByText('Connecting');
}

function indicator() {
  return screen.getByTestId('connection-status');
}

function dotClass() {
  return indicator().firstElementChild?.className;
}

function connectedClient() {
  act(() => FakeStompClient.latest.connect());
}

describe('SocketProvider', () => {
  beforeEach(() => {
    FakeStompClient.reset();
    localStorage.setItem(ACCESS_TOKEN_KEY, 'jwt-1');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ id: 7 }), {
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
  });

  afterEach(() => {
    vi.useRealTimers();
    document.title = '';
  });

  it('shows connecting, connected, reconnecting and offline in the indicator', async () => {
    await renderApp();
    expect(indicator()).toHaveAttribute('data-status', 'connecting');

    connectedClient();
    expect(indicator()).toHaveTextContent('Connected');
    expect(dotClass()).toContain('bg-emerald-500');

    act(() => FakeStompClient.latest.drop());
    expect(indicator()).toHaveTextContent('Reconnecting');
    expect(dotClass()).toContain('bg-amber-500');
  });

  it('shows offline and sends the user to login when the token is rejected', async () => {
    await renderApp();

    act(() => FakeStompClient.latest.reject());

    expect(indicator()).toHaveTextContent('Offline');
    expect(dotClass()).toContain('bg-red-500');
    expect(screen.getByTestId('path')).toHaveTextContent('/login');
    expect(localStorage.getItem(ACCESS_TOKEN_KEY)).toBeNull();
    expect(FakeStompClient.instances).toHaveLength(1);
  });

  it('tells the user a move was not sent while the connection is down', async () => {
    await renderApp();
    connectedClient();
    act(() => FakeStompClient.latest.drop());

    fireEvent.click(screen.getByRole('button', { name: 'Send move' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Move not sent');
    expect(FakeStompClient.latest.published).toEqual([]);
  });

  it('sends the move without a warning while connected', async () => {
    await renderApp();
    connectedClient();

    fireEvent.click(screen.getByRole('button', { name: 'Send move' }));

    expect(screen.queryByRole('alert')).toBeNull();
    expect(FakeStompClient.latest.published).toEqual([
      {
        destination: '/app/match/1/move',
        body: JSON.stringify({ playerId: 'p1' }),
      },
    ]);
  });

  it('keeps a subscription alive across a drop and reconnect', async () => {
    await renderApp();
    connectedClient();
    fireEvent.click(screen.getByRole('button', { name: 'Subscribe' }));
    vi.useFakeTimers();

    act(() => FakeStompClient.latest.drop());
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    connectedClient();
    act(() =>
      FakeStompClient.latest.deliver('/topic/matches/1', { moveCount: 3 }),
    );

    expect(FakeStompClient.instances).toHaveLength(2);
    expect(document.title).toBe(JSON.stringify({ moveCount: 3 }));
  });

  it('stays offline and does not connect when nobody is signed in', () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);

    render(
      <MemoryRouter>
        <AuthProvider>
          <SocketProvider createClient={FakeStompClient.create}>
            <ConnectionStatus />
          </SocketProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(indicator()).toHaveTextContent('Offline');
    expect(FakeStompClient.instances).toHaveLength(0);
  });
});
