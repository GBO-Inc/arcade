import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ACCESS_TOKEN_KEY } from '../api/client';
import { AuthProvider } from '../hooks/AuthContext';
import { SocketProvider } from '../hooks/SocketContext';
import { FakeStompClient } from '../realtime/fakeStompClient';
import { LobbyPage } from './LobbyPage';

const ROOM = {
  id: 1,
  code: 'ABC234',
  gameType: 'TIC_TAC_TOE',
  status: 'OPEN',
  creatorName: 'Alice',
  capacity: 2,
  freeSeats: 1,
};

const ROOM_TWO = {
  ...ROOM,
  id: 2,
  code: 'DEF234',
  gameType: 'CHECKERS',
  creatorName: 'Bob',
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function renderLobby(state?: { notice: string | null }) {
  localStorage.setItem(ACCESS_TOKEN_KEY, 'valid-token');
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/lobby', state }]}>
      <AuthProvider>
        <SocketProvider createClient={FakeStompClient.create}>
          <Routes>
            <Route path="/lobby" element={<LobbyPage />} />
            <Route path="/rooms/:id" element={<h1>Waiting room page</h1>} />
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

describe('LobbyPage', () => {
  beforeEach(() => {
    FakeStompClient.reset();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockImplementation((input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/api/me')) {
          return Promise.resolve(
            jsonResponse({ id: 7, username: 'alice', displayName: 'Alice' }),
          );
        }
        if (url.endsWith('/api/rooms')) {
          return Promise.resolve(jsonResponse([ROOM]));
        }
        if (url.endsWith('/api/rooms/join')) {
          return Promise.resolve(
            jsonResponse({ message: 'No open room with that code' }, 404),
          );
        }
        return Promise.resolve(
          jsonResponse({ message: 'Unexpected request' }, 500),
        );
      }),
    );
  });

  it('renders open rooms and uses the expected room list request', async () => {
    const fetchMock = vi.mocked(fetch);
    renderLobby();

    expect(await screen.findByText('Tic-Tac-Toe')).toBeVisible();
    expect(screen.getByText('Created by Alice')).toBeVisible();
    expect(screen.getByText('1/2 seats')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Join' })).toBeVisible();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rooms',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('shows an empty state and opens the create room dialog from its call to action', async () => {
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      return Promise.resolve(
        jsonResponse(url.endsWith('/api/me') ? { id: 7 } : []),
      );
    });
    renderLobby();

    expect(await screen.findByText('No open rooms right now.')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Create a room' }));

    expect(screen.getByRole('dialog', { name: 'Create room' })).toBeVisible();
    expect(screen.getByRole('radio', { name: 'Tic-Tac-Toe' })).toBeChecked();
    expect(screen.getByRole('radio', { name: 'Connect Four' })).toBeVisible();
    expect(screen.getByRole('radio', { name: 'Checkers' })).toBeVisible();
  });

  it('creates a selected game and opens its waiting room', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation(
      (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/me')) {
          return Promise.resolve(jsonResponse({ id: 7 }));
        }
        if (url.endsWith('/api/rooms') && init?.method === 'POST') {
          return Promise.resolve(jsonResponse(ROOM, 201));
        }
        return Promise.resolve(jsonResponse([ROOM]));
      },
    );
    renderLobby();

    fireEvent.click(await screen.findByRole('button', { name: 'Create room' }));
    fireEvent.click(screen.getByRole('radio', { name: 'Checkers' }));
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Create room',
      }),
    );

    expect(await screen.findByText('Waiting room page')).toBeVisible();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rooms',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ gameType: 'CHECKERS' }),
      }),
    );
  });

  it('opens the waiting room after joining by code', async () => {
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse({ id: 7 }));
      }
      if (url.endsWith('/api/rooms/join')) {
        return Promise.resolve(jsonResponse(ROOM));
      }
      return Promise.resolve(jsonResponse([ROOM]));
    });
    renderLobby();

    fireEvent.change(
      await screen.findByRole('textbox', { name: 'Room code' }),
      { target: { value: 'ABC234' } },
    );
    fireEvent.click(screen.getByRole('button', { name: 'Join room' }));

    expect(await screen.findByText('Waiting room page')).toBeVisible();
  });

  it('opens the waiting room after joining from a room row', async () => {
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse({ id: 7 }));
      }
      if (url.endsWith('/api/rooms/join')) {
        return Promise.resolve(jsonResponse(ROOM));
      }
      return Promise.resolve(jsonResponse([ROOM]));
    });
    renderLobby();

    fireEvent.click(await screen.findByRole('button', { name: 'Join' }));

    expect(await screen.findByText('Waiting room page')).toBeVisible();
  });

  it('shows the message left by a closed waiting room', async () => {
    renderLobby({ notice: 'The room was closed by its creator.' });

    expect(
      await screen.findByText('The room was closed by its creator.'),
    ).toBeVisible();
  });

  it('validates room codes and sends normalized valid codes', async () => {
    const fetchMock = vi.mocked(fetch);
    renderLobby();

    const codeField = await screen.findByRole('textbox', { name: 'Room code' });
    fireEvent.change(codeField, { target: { value: 'abc-12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Join room' }));

    expect(
      await screen.findByText('Enter a valid 6-character room code'),
    ).toBeVisible();
    expect(codeField).toHaveAttribute('aria-invalid', 'true');
    expect(codeField).toHaveAccessibleDescription(
      'Enter a valid 6-character room code',
    );
    expect(fetchMock).not.toHaveBeenCalledWith(
      '/api/rooms/join',
      expect.anything(),
    );

    fireEvent.change(codeField, { target: { value: ' xyz234 ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Join room' }));

    expect(
      await screen.findByText('No open room with that code'),
    ).toBeVisible();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/rooms/join',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ code: 'XYZ234' }),
      }),
    );
  });

  it('shows the server error when joining a full room by code', async () => {
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse({ id: 7 }));
      }
      if (url.endsWith('/api/rooms/join')) {
        return Promise.resolve(jsonResponse({ message: 'Room is full' }, 409));
      }
      return Promise.resolve(jsonResponse([ROOM]));
    });
    renderLobby();

    fireEvent.change(
      await screen.findByRole('textbox', { name: 'Room code' }),
      {
        target: { value: 'ABC234' },
      },
    );
    fireEvent.click(screen.getByRole('button', { name: 'Join room' }));

    expect(await screen.findByText('Room is full')).toBeVisible();
  });

  it('shows server errors when joining from a room row', async () => {
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse({ id: 7 }));
      }
      if (url.endsWith('/api/rooms/join')) {
        return Promise.resolve(
          jsonResponse({ message: 'You are already in another room' }, 409),
        );
      }
      return Promise.resolve(jsonResponse([ROOM]));
    });
    renderLobby();

    fireEvent.click(await screen.findByRole('button', { name: 'Join' }));

    expect(
      await screen.findByText('You are already in another room'),
    ).toBeVisible();
  });

  it('updates the visible list after room-list invalidation events', async () => {
    let currentRooms = [ROOM];
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse({ id: 7 }));
      }
      return Promise.resolve(jsonResponse(currentRooms));
    });
    renderLobby();
    await screen.findByText('Tic-Tac-Toe');
    await connectSocket();

    currentRooms = [ROOM_TWO];
    act(() => {
      FakeStompClient.latest.deliver('/topic/rooms', {
        type: 'ROOM_LIST_CHANGED',
      });
    });

    expect(await screen.findByText('Checkers')).toBeVisible();
    expect(screen.queryByText('Tic-Tac-Toe')).not.toBeInTheDocument();

    currentRooms = [];
    act(() => {
      FakeStompClient.latest.deliver('/topic/rooms', {
        type: 'ROOM_LIST_CHANGED',
      });
    });

    expect(await screen.findByText('No open rooms right now.')).toBeVisible();
    expect(screen.queryByText('Checkers')).not.toBeInTheDocument();
  });

  it('shows loading and fetch error states with a retry option', async () => {
    let resolveRooms: ((response: Response) => void) | undefined;
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse({ id: 7 }));
      }
      return new Promise<Response>((resolve) => {
        resolveRooms = resolve;
      });
    });
    renderLobby();

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Loading rooms',
    );
    await act(async () => {
      resolveRooms?.(jsonResponse({ message: 'Rooms unavailable' }, 503));
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Rooms unavailable',
    );
    expect(screen.getByRole('button', { name: 'Retry' })).toBeVisible();
  });

  it('shows a lost-connection banner while the socket reconnects', async () => {
    let currentRooms = [ROOM];
    vi.mocked(fetch).mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/me')) {
        return Promise.resolve(jsonResponse({ id: 7 }));
      }
      return Promise.resolve(jsonResponse(currentRooms));
    });
    renderLobby();
    await screen.findByText('Tic-Tac-Toe');
    await connectSocket();
    await waitFor(() => {
      expect(
        screen.queryByText(
          'Lost connection to the server. Room updates may be delayed.',
        ),
      ).not.toBeInTheDocument();
    });

    act(() => FakeStompClient.latest.drop());

    expect(screen.getByRole('alert')).toBeVisible();
    currentRooms = [ROOM_TWO];
    await waitFor(() => expect(FakeStompClient.instances).toHaveLength(2), {
      timeout: 3000,
    });
    act(() => FakeStompClient.latest.connect());

    expect(await screen.findByText('Checkers')).toBeVisible();
    expect(screen.queryByText('Tic-Tac-Toe')).not.toBeInTheDocument();
  });
});
