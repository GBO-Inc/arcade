import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_TOKEN_KEY } from '../api/client';
import type { RoomDetail } from '../api/rooms';
import { AuthProvider } from '../hooks/AuthContext';
import { SocketProvider } from '../hooks/SocketContext';
import { FakeStompClient } from '../realtime/fakeStompClient';
import { WaitingRoomPage } from './WaitingRoomPage';

const ALICE = {
  userId: 7,
  displayName: 'Alice',
  avatarColour: '#3B82F6',
  ready: false,
};
const BOB = {
  userId: 8,
  displayName: 'Bob',
  avatarColour: '#EF4444',
  ready: false,
};

const FULL_ROOM: RoomDetail = {
  id: 42,
  code: 'ABC234',
  gameType: 'TIC_TAC_TOE',
  status: 'FULL',
  creatorId: 7,
  capacity: 2,
  matchId: null,
  seats: [ALICE, BOB],
};

const TOPIC = '/topic/rooms/42';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

interface Api {
  room: RoomDetail | Response;
  userId: number;
  respond?: (url: string, init?: RequestInit) => Response | undefined;
}

function mockApi({ room, userId, respond }: Api) {
  vi.stubGlobal(
    'fetch',
    vi
      .fn()
      .mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/me')) {
          return Promise.resolve(jsonResponse({ id: userId, username: 'me' }));
        }
        const custom = respond?.(url, init);
        if (custom) {
          return Promise.resolve(custom);
        }
        if (url.endsWith('/api/rooms/42')) {
          return Promise.resolve(
            room instanceof Response ? room : jsonResponse(room),
          );
        }
        return Promise.resolve(jsonResponse({ message: 'Unexpected' }, 500));
      }),
  );
}

function LobbyStub() {
  const { state } = useLocation() as {
    state: { notice: string | null } | null;
  };
  return (
    <div>
      <h1>Lobby page</h1>
      {state?.notice && <p>{state.notice}</p>}
    </div>
  );
}

function renderRoom() {
  localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-token');
  render(
    <MemoryRouter initialEntries={['/rooms/42']}>
      <AuthProvider>
        <SocketProvider createClient={FakeStompClient.create}>
          <Routes>
            <Route path="/rooms/:id" element={<WaitingRoomPage />} />
            <Route path="/lobby" element={<LobbyStub />} />
            <Route path="/match/:id" element={<h1>Match page</h1>} />
          </Routes>
        </SocketProvider>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function connectSocket() {
  await waitFor(() => expect(FakeStompClient.instances).toHaveLength(1));
  act(() => FakeStompClient.latest.connect());
}

function deliver(payload: unknown) {
  act(() => FakeStompClient.latest.deliver(TOPIC, payload));
}

function calls(path: string) {
  return vi
    .mocked(fetch)
    .mock.calls.filter(([url]) => String(url).endsWith(path));
}

describe('WaitingRoomPage', () => {
  beforeEach(() => {
    FakeStompClient.reset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows both seats with names, colours, ready state and the you marker', async () => {
    mockApi({
      userId: 7,
      room: { ...FULL_ROOM, seats: [ALICE, { ...BOB, ready: true }] },
    });
    renderRoom();

    const alice = await screen.findByRole('group', { name: 'Alice' });
    const bob = screen.getByRole('group', { name: 'Bob' });
    expect(alice).toHaveTextContent('you');
    expect(alice).toHaveTextContent('Blue');
    expect(alice).toHaveTextContent('Not ready');
    expect(bob).not.toHaveTextContent('you');
    expect(bob).toHaveTextContent('Red');
    expect(bob).toHaveTextContent('Ready');
    expect(screen.getByLabelText('Room code')).toHaveTextContent('ABC234');
    expect(screen.getByText('Tic-Tac-Toe')).toBeVisible();
  });

  it('shows an empty seat placeholder and keeps Ready disabled until the room is full', async () => {
    mockApi({
      userId: 7,
      room: { ...FULL_ROOM, status: 'OPEN', seats: [ALICE] },
    });
    renderRoom();

    expect(await screen.findByText('Waiting for a player…')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Ready' })).toBeDisabled();
  });

  it('copies the room code and confirms it', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
    mockApi({ userId: 7, room: FULL_ROOM });
    renderRoom();

    fireEvent.click(await screen.findByRole('button', { name: 'Copy code' }));

    expect(writeText).toHaveBeenCalledWith('ABC234');
    expect(await screen.findByText('Copied')).toBeVisible();
  });

  it('tells the player when the code could not be copied', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'));
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } });
    mockApi({ userId: 7, room: FULL_ROOM });
    renderRoom();

    fireEvent.click(await screen.findByRole('button', { name: 'Copy code' }));

    expect(await screen.findByText('Could not copy')).toBeVisible();
  });

  it('sends the ready request and shows the other player ready through the live event', async () => {
    mockApi({
      userId: 7,
      room: FULL_ROOM,
      respond: (url) =>
        url.endsWith('/api/rooms/42/ready')
          ? jsonResponse({
              ...FULL_ROOM,
              seats: [{ ...ALICE, ready: true }, BOB],
            })
          : undefined,
    });
    renderRoom();
    await connectSocket();

    fireEvent.click(await screen.findByRole('button', { name: 'Ready' }));

    await waitFor(() =>
      expect(screen.getByRole('group', { name: 'Alice' })).toHaveTextContent(
        'Ready',
      ),
    );
    expect(calls('/api/rooms/42/ready')[0][1]).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ ready: true }),
    });
    expect(screen.getByRole('button', { name: 'Ready' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('group', { name: 'Bob' })).toHaveTextContent(
      'Not ready',
    );

    deliver({
      type: 'ROOM_UPDATED',
      room: {
        ...FULL_ROOM,
        seats: [
          { ...ALICE, ready: true },
          { ...BOB, ready: true },
        ],
      },
    });

    const bob = screen.getByRole('group', { name: 'Bob' });
    expect(bob).toHaveTextContent(/Ready$/);
    expect(bob).not.toHaveTextContent('Not ready');
  });

  it('sends ready false when the player is already ready', async () => {
    mockApi({
      userId: 7,
      room: { ...FULL_ROOM, seats: [{ ...ALICE, ready: true }, BOB] },
      respond: (url) =>
        url.endsWith('/api/rooms/42/ready')
          ? jsonResponse(FULL_ROOM)
          : undefined,
    });
    renderRoom();

    fireEvent.click(await screen.findByRole('button', { name: 'Ready' }));

    await waitFor(() => expect(calls('/api/rooms/42/ready')).toHaveLength(1));
    expect(calls('/api/rooms/42/ready')[0][1]).toMatchObject({
      body: JSON.stringify({ ready: false }),
    });
  });

  it('opens the match when the match started event arrives', async () => {
    mockApi({ userId: 7, room: FULL_ROOM });
    renderRoom();
    await screen.findByRole('group', { name: 'Alice' });
    await connectSocket();

    deliver({ type: 'MATCH_STARTED', matchId: 'm-1' });

    expect(await screen.findByText('Match page')).toBeVisible();
  });

  it('opens the match when the loaded room has already started', async () => {
    mockApi({
      userId: 7,
      room: { ...FULL_ROOM, status: 'STARTED', matchId: 'm-9' },
    });
    renderRoom();

    expect(await screen.findByText('Match page')).toBeVisible();
  });

  it('goes back to the lobby with a message when the creator closes the room', async () => {
    mockApi({ userId: 8, room: FULL_ROOM });
    renderRoom();
    await screen.findByRole('group', { name: 'Alice' });
    await connectSocket();

    deliver({ type: 'ROOM_CLOSED' });

    expect(await screen.findByText('Lobby page')).toBeVisible();
    expect(
      screen.getByText('The room was closed by its creator.'),
    ).toBeVisible();
  });

  it('goes back to the lobby without a message after closing its own room', async () => {
    mockApi({
      userId: 7,
      room: FULL_ROOM,
      respond: (url) =>
        url.endsWith('/api/rooms/42/close')
          ? new Response(null, { status: 204 })
          : undefined,
    });
    renderRoom();
    await connectSocket();

    fireEvent.click(await screen.findByRole('button', { name: /Close room/ }));
    await waitFor(() => expect(calls('/api/rooms/42/close')).toHaveLength(1));
    deliver({ type: 'ROOM_CLOSED' });

    expect(await screen.findByText('Lobby page')).toBeVisible();
    expect(screen.queryByText(/closed by its creator/)).not.toBeInTheDocument();
  });

  it('offers closing the room only to its creator', async () => {
    mockApi({ userId: 8, room: FULL_ROOM });
    renderRoom();

    await screen.findByRole('group', { name: 'Alice' });
    expect(
      screen.queryByRole('button', { name: /Close room/ }),
    ).not.toBeInTheDocument();
  });

  it('leaves the room and returns to the lobby', async () => {
    mockApi({
      userId: 8,
      room: FULL_ROOM,
      respond: (url) =>
        url.endsWith('/api/rooms/42/leave')
          ? new Response(null, { status: 204 })
          : undefined,
    });
    renderRoom();

    fireEvent.click(await screen.findByRole('button', { name: 'Leave' }));

    expect(await screen.findByText('Lobby page')).toBeVisible();
    expect(calls('/api/rooms/42/leave')).toHaveLength(1);
  });

  it('shows a server error from a failed action', async () => {
    mockApi({
      userId: 7,
      room: FULL_ROOM,
      respond: (url) =>
        url.endsWith('/api/rooms/42/ready')
          ? jsonResponse(
              { message: 'The game in this room has already started' },
              409,
            )
          : undefined,
    });
    renderRoom();

    fireEvent.click(await screen.findByRole('button', { name: 'Ready' }));

    expect(
      await screen.findByText('The game in this room has already started'),
    ).toBeVisible();
  });

  it('refetches the room after the socket reconnects', async () => {
    mockApi({ userId: 7, room: FULL_ROOM });
    renderRoom();
    await screen.findByRole('group', { name: 'Alice' });
    await connectSocket();
    expect(calls('/api/rooms/42')).toHaveLength(1);

    act(() => FakeStompClient.latest.drop());
    await waitFor(() => expect(FakeStompClient.instances).toHaveLength(2), {
      timeout: 3000,
    });
    act(() => FakeStompClient.latest.connect());

    await waitFor(() => expect(calls('/api/rooms/42')).toHaveLength(2));
  });

  it('sends the player to the lobby when the room no longer exists', async () => {
    mockApi({
      userId: 7,
      room: jsonResponse({ message: 'Room not found' }, 404),
    });
    renderRoom();

    expect(await screen.findByText('Lobby page')).toBeVisible();
    expect(screen.getByText('That room is no longer available.')).toBeVisible();
  });

  it('explains why the room cannot be opened', async () => {
    mockApi({
      userId: 7,
      room: jsonResponse({ message: 'You are not in that room' }, 403),
    });
    renderRoom();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You are not in that room',
    );
  });
});
