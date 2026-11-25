import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { getRoom, parseRoomEvent, type RoomDetail } from '../api/rooms';
import { useSocket } from './socket';

export type RoomLoad =
  | { phase: 'loading' }
  | { phase: 'failed'; message: string }
  | { phase: 'ready'; room: RoomDetail };

export interface WaitingRoomHandlers {
  onMatchStarted: (matchId: string) => void;
  /** `closed`: the creator closed it. `gone`: it no longer exists. */
  onClosed: (reason: 'closed' | 'gone') => void;
}

function failure(error: unknown): RoomLoad {
  const message =
    error instanceof Error ? error.message : 'Could not load the room';
  return { phase: 'failed', message };
}

/**
 * Loads a room, then follows its topic. The page only shows what the server sent. The room is
 * loaded again after a reconnect, because events sent while offline are lost.
 */
export function useWaitingRoom(
  roomId: string,
  { onMatchStarted, onClosed }: WaitingRoomHandlers,
) {
  const { status, subscribe } = useSocket();
  const [load, setLoad] = useState<RoomLoad>({ phase: 'loading' });
  const handlers = useRef({ onMatchStarted, onClosed });
  const active = useRef(true);
  const previousStatus = useRef(status);

  useEffect(() => {
    handlers.current = { onMatchStarted, onClosed };
  }, [onMatchStarted, onClosed]);

  const applyRoom = useCallback((room: RoomDetail) => {
    setLoad({ phase: 'ready', room });
    if (room.matchId) {
      handlers.current.onMatchStarted(room.matchId);
    }
  }, []);

  const refresh = useCallback(async () => {
    try {
      const room = await getRoom(roomId);
      if (active.current) {
        applyRoom(room);
      }
    } catch (error: unknown) {
      if (!active.current) {
        return;
      }
      if (error instanceof ApiError && error.status === 404) {
        handlers.current.onClosed('gone');
        return;
      }
      setLoad((current) =>
        current.phase === 'ready' ? current : failure(error),
      );
    }
  }, [applyRoom, roomId]);

  useEffect(() => {
    active.current = true;
    void refresh();
    return () => {
      active.current = false;
    };
  }, [refresh]);

  useEffect(
    () =>
      subscribe(`/topic/rooms/${roomId}`, (body) => {
        const event = parseRoomEvent(body);
        if (event?.type === 'ROOM_UPDATED') {
          applyRoom(event.room);
        } else if (event?.type === 'MATCH_STARTED') {
          handlers.current.onMatchStarted(event.matchId);
        } else if (event?.type === 'ROOM_CLOSED') {
          handlers.current.onClosed('closed');
        }
      }),
    [applyRoom, roomId, subscribe],
  );

  useEffect(() => {
    if (previousStatus.current === 'reconnecting' && status === 'connected') {
      void refresh();
    }
    previousStatus.current = status;
  }, [refresh, status]);

  return { load, applyRoom };
}
