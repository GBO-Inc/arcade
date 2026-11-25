import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { colourName } from '../api/profile';
import { ApiError } from '../api/client';
import {
  closeRoom,
  GAMES,
  leaveRoom,
  setReady,
  type RoomDetail,
  type RoomSeat,
} from '../api/rooms';
import { Button, Card } from '../components/ui';
import { useAuth } from '../hooks/auth';
import { useWaitingRoom } from '../hooks/useWaitingRoom';

const NO_COLOUR = '#9CA3AF';
const COPIED_MESSAGE_MS = 2000;

type Pending = 'ready' | 'leave' | 'close';
type CopyState = 'idle' | 'copied' | 'failed';

function errorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error
    ? error.message
    : fallback;
}

export function WaitingRoomPage() {
  const { id } = useParams();
  const { user } = useAuth();
  if (!id || user?.id === undefined) {
    return null;
  }
  // The key gives each room a fresh component, so no state from the previous one leaks in.
  return <WaitingRoomView key={id} roomId={id} myUserId={Number(user.id)} />;
}

function WaitingRoomView({
  roomId,
  myUserId,
}: {
  roomId: string;
  myUserId: number;
}) {
  const navigate = useNavigate();
  const [pending, setPending] = useState<Pending | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  // Closing the room ourselves also sends us the closed event, which must not show the message.
  const closingOwnRoom = useRef(false);
  const left = useRef(false);

  const onMatchStarted = useCallback(
    (matchId: string) => {
      if (!left.current) {
        left.current = true;
        navigate(`/match/${matchId}`, { replace: true });
      }
    },
    [navigate],
  );
  const onClosed = useCallback(
    (reason: 'closed' | 'gone') => {
      if (left.current) {
        return;
      }
      left.current = true;
      const notice = closingOwnRoom.current
        ? null
        : reason === 'closed'
          ? 'The room was closed by its creator.'
          : 'That room is no longer available.';
      navigate('/lobby', { replace: true, state: { notice } });
    },
    [navigate],
  );
  const { load, applyRoom } = useWaitingRoom(roomId, {
    onMatchStarted,
    onClosed,
  });

  async function run(action: Pending, task: () => Promise<void>) {
    setActionError(null);
    setPending(action);
    try {
      await task();
    } catch (error: unknown) {
      closingOwnRoom.current = false;
      setActionError(errorMessage(error, 'Something went wrong'));
    } finally {
      setPending(null);
    }
  }

  function leaveToLobby() {
    left.current = true;
    navigate('/lobby', { replace: true });
  }

  if (load.phase === 'loading') {
    return <p role="status">Loading room…</p>;
  }
  if (load.phase === 'failed') {
    return (
      <div role="alert" className="space-y-ui-2">
        <h1 className="text-xl font-bold">Cannot open this room</h1>
        <p>{load.message}</p>
      </div>
    );
  }

  const { room } = load;
  const me = room.seats.find((seat) => seat.userId === myUserId);
  const isCreator = room.creatorId === myUserId;
  const roomIsFull = room.seats.length >= room.capacity;

  return (
    <div className="mx-auto max-w-2xl space-y-ui-6">
      <header className="space-y-ui-2">
        <h1 className="text-ui-2xl font-bold">Waiting room</h1>
        <p className="text-ui-muted">
          {GAMES.find((game) => game.type === room.gameType)?.label ??
            room.gameType}
        </p>
      </header>

      <RoomCode code={room.code} />

      <ul className="grid gap-ui-4 sm:grid-cols-2" aria-label="Seats">
        {Array.from({ length: room.capacity }, (_, index) => (
          <li key={index}>
            <Seat
              seat={room.seats[index]}
              isMe={room.seats[index]?.userId === myUserId}
            />
          </li>
        ))}
      </ul>

      {!roomIsFull && (
        <p className="text-ui-muted">
          Waiting for another player. Share the room code to invite someone.
        </p>
      )}
      {actionError && (
        <p role="alert" className="text-ui-error">
          {actionError}
        </p>
      )}

      <div className="flex flex-wrap gap-ui-3">
        <Button
          aria-pressed={me?.ready ?? false}
          loading={pending === 'ready'}
          disabled={!me || !roomIsFull || pending !== null}
          onClick={() =>
            void run('ready', async () => {
              applyRoom(await setReady(room.id, !me?.ready));
            })
          }
        >
          Ready
        </Button>
        <Button
          variant="secondary"
          loading={pending === 'leave'}
          disabled={pending !== null}
          onClick={() =>
            void run('leave', async () => {
              await leaveRoom(room.id);
              leaveToLobby();
            })
          }
        >
          Leave
        </Button>
        {isCreator && (
          <CloseButton
            room={room}
            pending={pending}
            onClose={() =>
              void run('close', async () => {
                closingOwnRoom.current = true;
                await closeRoom(room.id);
                leaveToLobby();
              })
            }
          />
        )}
      </div>
    </div>
  );
}

function CloseButton({
  room,
  pending,
  onClose,
}: {
  room: RoomDetail;
  pending: Pending | null;
  onClose: () => void;
}) {
  return (
    <Button
      variant="danger"
      aria-label={`Close room ${room.code}`}
      loading={pending === 'close'}
      disabled={pending !== null}
      onClick={onClose}
    >
      Close room
    </Button>
  );
}

function RoomCode({ code }: { code: string }) {
  const [copy, setCopy] = useState<CopyState>('idle');
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  async function copyCode() {
    try {
      await navigator.clipboard.writeText(code);
      setCopy('copied');
    } catch {
      setCopy('failed');
    }
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => setCopy('idle'), COPIED_MESSAGE_MS);
  }

  return (
    <Card className="flex flex-wrap items-center justify-between gap-ui-4">
      <div>
        <p className="text-ui-sm text-ui-muted">Room code</p>
        <p
          aria-label="Room code"
          className="font-mono text-ui-2xl font-bold tracking-widest"
        >
          {code}
        </p>
      </div>
      <div className="flex items-center gap-ui-3">
        <span role="status" className="text-ui-sm text-ui-muted">
          {copy === 'copied' && 'Copied'}
          {copy === 'failed' && 'Could not copy'}
        </span>
        <Button variant="secondary" onClick={() => void copyCode()}>
          Copy code
        </Button>
      </div>
    </Card>
  );
}

function Seat({ seat, isMe }: { seat?: RoomSeat; isMe: boolean }) {
  if (!seat) {
    return (
      <Card className="flex h-full items-center border-dashed text-ui-muted">
        Waiting for a player…
      </Card>
    );
  }
  return (
    <Card
      role="group"
      aria-label={seat.displayName}
      className="flex items-center gap-ui-3"
    >
      <span
        aria-hidden="true"
        className="size-ui-8 shrink-0 rounded-full"
        style={{ backgroundColor: seat.avatarColour ?? NO_COLOUR }}
      />
      <div>
        <p className="font-semibold">
          {seat.displayName}
          {isMe && (
            <span className="ml-ui-2 text-ui-sm text-ui-muted">you</span>
          )}
        </p>
        <p className="text-ui-sm text-ui-muted">
          {colourName(seat.avatarColour)}
        </p>
        <p
          className={`text-ui-sm ${seat.ready ? 'text-ui-success-text' : 'text-ui-muted'}`}
        >
          {seat.ready ? 'Ready' : 'Not ready'}
        </p>
      </div>
    </Card>
  );
}
