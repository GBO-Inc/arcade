import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import {
  createRoom,
  joinRoom,
  listRooms,
  ROOM_CODE_PATTERN,
  type GameType,
  type Room,
} from '../api/rooms';
import { Button, Card, Dialog, TextField } from '../components/ui';
import { useSocket } from '../hooks/socket';

const games: { type: GameType; label: string }[] = [
  { type: 'TIC_TAC_TOE', label: 'Tic-Tac-Toe' },
  { type: 'CONNECT_FOUR', label: 'Connect Four' },
  { type: 'CHECKERS', label: 'Checkers' },
];

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    return error.message;
  }
  return error instanceof Error ? error.message : fallback;
}

function isRoomListChangedEvent(body: unknown) {
  return (
    typeof body === 'object' &&
    body !== null &&
    'type' in body &&
    body.type === 'ROOM_LIST_CHANGED'
  );
}

export function LobbyPage() {
  const { status, subscribe } = useSocket();
  const [rooms, setRooms] = useState<Room[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [selectedGame, setSelectedGame] = useState<GameType>('TIC_TAC_TOE');
  const [createPending, setCreatePending] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [createdRoom, setCreatedRoom] = useState<Room | null>(null);
  const [code, setCode] = useState('');
  const [codeError, setCodeError] = useState<string | null>(null);
  const [codePending, setCodePending] = useState(false);
  const [rowPending, setRowPending] = useState<number | null>(null);
  const [rowError, setRowError] = useState<{
    roomId: number;
    message: string;
  } | null>(null);
  const requestCount = useRef(0);
  const active = useRef(true);
  const previousStatus = useRef(status);

  const refreshRooms = useCallback(async () => {
    const requestId = ++requestCount.current;
    setLoading(true);
    setLoadError(null);
    try {
      const currentRooms = await listRooms();
      if (active.current && requestId === requestCount.current) {
        setRooms(currentRooms);
      }
    } catch (error: unknown) {
      if (active.current && requestId === requestCount.current) {
        setLoadError(errorMessage(error, 'Could not load rooms'));
      }
    } finally {
      if (active.current && requestId === requestCount.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    active.current = true;
    void Promise.resolve().then(() => {
      if (active.current) {
        return refreshRooms();
      }
    });
    return () => {
      active.current = false;
      requestCount.current += 1;
    };
  }, [refreshRooms]);

  useEffect(
    () =>
      subscribe('/topic/rooms', (body) => {
        if (isRoomListChangedEvent(body)) {
          void refreshRooms();
        }
      }),
    [refreshRooms, subscribe],
  );

  useEffect(() => {
    if (previousStatus.current === 'reconnecting' && status === 'connected') {
      void refreshRooms();
    }
    previousStatus.current = status;
  }, [refreshRooms, status]);

  async function handleCreate(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError(null);
    setCreatedRoom(null);
    setCreatePending(true);
    try {
      const room = await createRoom(selectedGame);
      if (active.current) {
        setCreatedRoom(room);
        void refreshRooms();
      }
    } catch (error: unknown) {
      if (active.current) {
        setCreateError(errorMessage(error, 'Could not create room'));
      }
    } finally {
      if (active.current) {
        setCreatePending(false);
      }
    }
  }

  async function joinRoomByCode(roomCode: string) {
    setCodeError(null);
    setCodePending(true);
    try {
      await joinRoom(roomCode);
      if (active.current) {
        setCode('');
        void refreshRooms();
      }
    } catch (error: unknown) {
      if (active.current) {
        setCodeError(errorMessage(error, 'Could not join room'));
      }
    } finally {
      if (active.current) {
        setCodePending(false);
      }
    }
  }

  async function handleCodeJoin(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedCode = code.trim().toUpperCase();
    setCode(normalizedCode);
    setCodeError(null);
    if (!ROOM_CODE_PATTERN.test(normalizedCode)) {
      setCodeError('Enter a valid 6-character room code');
      return;
    }
    await joinRoomByCode(normalizedCode);
  }

  async function handleRowJoin(room: Room) {
    setRowError(null);
    setRowPending(room.id);
    try {
      await joinRoom(room.code);
      if (active.current) {
        void refreshRooms();
      }
    } catch (error: unknown) {
      if (active.current) {
        setRowError({
          roomId: room.id,
          message: errorMessage(error, 'Could not join room'),
        });
      }
    } finally {
      if (active.current) {
        setRowPending(null);
      }
    }
  }

  function closeCreateDialog() {
    if (createPending) {
      return;
    }
    setCreateDialogOpen(false);
    setCreateError(null);
    setCreatedRoom(null);
    setSelectedGame('TIC_TAC_TOE');
  }

  const disconnected = status === 'reconnecting' || status === 'offline';

  return (
    <div className="space-y-ui-6">
      <header className="flex flex-wrap items-center justify-between gap-ui-4">
        <h1 className="text-ui-2xl font-bold">Lobby</h1>
        <Button onClick={() => setCreateDialogOpen(true)}>Create room</Button>
      </header>

      {disconnected && (
        <div
          role="alert"
          className="rounded-ui-lg border border-ui-error-border bg-ui-error-surface px-ui-4 py-ui-3 text-ui-error-text"
        >
          Lost connection to the server. Room updates may be delayed.
        </div>
      )}

      <section aria-labelledby="open-rooms-heading" className="space-y-ui-3">
        <h2 id="open-rooms-heading" className="text-ui-lg font-semibold">
          Open rooms
        </h2>
        {loading ? (
          <p role="status" className="text-ui-muted">
            Loading rooms…
          </p>
        ) : loadError ? (
          <div className="space-y-ui-3">
            <p role="alert" className="text-ui-error">
              {loadError}
            </p>
            <Button variant="secondary" onClick={() => void refreshRooms()}>
              Retry
            </Button>
          </div>
        ) : rooms.length === 0 ? (
          <Card className="space-y-ui-3 text-center">
            <p className="text-ui-muted">No open rooms right now.</p>
            <Button onClick={() => setCreateDialogOpen(true)}>
              Create a room
            </Button>
          </Card>
        ) : (
          <ul className="space-y-ui-3">
            {rooms.map((room) => (
              <li key={room.id}>
                <Card className="flex flex-wrap items-center justify-between gap-ui-4">
                  <div className="space-y-ui-1">
                    <h3 className="font-semibold">
                      {games.find((game) => game.type === room.gameType)
                        ?.label ?? room.gameType}
                    </h3>
                    <p className="text-ui-sm text-ui-muted">
                      Created by {room.creatorName}
                    </p>
                    <p className="text-ui-sm text-ui-muted">
                      {room.capacity - room.freeSeats}/{room.capacity} seats
                    </p>
                    {rowError?.roomId === room.id && (
                      <p role="alert" className="text-ui-sm text-ui-error">
                        {rowError.message}
                      </p>
                    )}
                  </div>
                  <Button
                    variant="secondary"
                    loading={rowPending === room.id}
                    disabled={rowPending !== null && rowPending !== room.id}
                    onClick={() => void handleRowJoin(room)}
                  >
                    Join
                  </Button>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="join-room-heading" className="space-y-ui-3">
        <h2 id="join-room-heading" className="text-ui-lg font-semibold">
          Join with code
        </h2>
        <form
          noValidate
          className="flex flex-wrap items-start gap-ui-3"
          onSubmit={(event) => void handleCodeJoin(event)}
        >
          <div className="min-w-60 flex-1">
            <TextField
              label="Room code"
              name="code"
              value={code}
              onChange={(event) => {
                setCode(event.target.value);
                setCodeError(null);
              }}
              error={codeError ?? undefined}
              autoComplete="off"
              className="uppercase"
            />
          </div>
          <Button type="submit" loading={codePending}>
            Join room
          </Button>
        </form>
      </section>

      <Dialog
        open={createDialogOpen}
        title={createdRoom ? 'Room created' : 'Create room'}
        onClose={closeCreateDialog}
      >
        {createdRoom ? (
          <div className="space-y-ui-4">
            <p>Share this code with the player you want to invite:</p>
            <p
              aria-label="Room code"
              className="rounded-ui-lg bg-ui-surface-raised px-ui-4 py-ui-3 text-center font-mono text-ui-2xl font-bold tracking-widest"
            >
              {createdRoom.code}
            </p>
            <Button onClick={closeCreateDialog}>Close</Button>
          </div>
        ) : (
          <form noValidate className="space-y-ui-4" onSubmit={handleCreate}>
            <fieldset className="space-y-ui-2">
              <legend className="text-ui-sm font-medium text-ui-text">
                Choose a game
              </legend>
              {games.map((game) => (
                <label key={game.type} className="flex items-center gap-ui-2">
                  <input
                    type="radio"
                    name="gameType"
                    value={game.type}
                    checked={selectedGame === game.type}
                    onChange={() => setSelectedGame(game.type)}
                  />
                  <span>{game.label}</span>
                </label>
              ))}
            </fieldset>
            {createError && (
              <p role="alert" className="text-ui-error">
                {createError}
              </p>
            )}
            <div className="flex justify-end gap-ui-3">
              <Button
                type="button"
                variant="secondary"
                disabled={createPending}
                onClick={closeCreateDialog}
              >
                Cancel
              </Button>
              <Button type="submit" loading={createPending}>
                Create room
              </Button>
            </div>
          </form>
        )}
      </Dialog>
    </div>
  );
}
