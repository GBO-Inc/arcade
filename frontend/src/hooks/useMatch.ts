import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { getMatch } from '../api/matches';
import {
  isErrorMessage,
  isFinalResult,
  isMatchState,
  isMoveEvent,
  type MatchFinalResult,
  type MatchState,
} from '../games/matchTypes';
import { useSocket } from './socket';

export type MatchLoad =
  | { phase: 'loading' }
  | { phase: 'failed'; notFound: boolean; message: string }
  | { phase: 'ready'; match: MatchState };

export interface ServerError {
  id: number;
  message: string;
}

function describeFailure(error: unknown): MatchLoad {
  if (error instanceof ApiError && error.status === 404) {
    return { phase: 'failed', notFound: true, message: 'Match not found' };
  }
  if (error instanceof ApiError && error.status === 403) {
    return { phase: 'failed', notFound: false, message: error.message };
  }
  const reason = error instanceof Error ? `: ${error.message}` : '';
  return {
    phase: 'failed',
    notFound: false,
    message: `Could not load the match${reason}`,
  };
}

/** Never goes back to an older state, e.g. the snapshot sent on subscribing after a move event. */
function newest(current: MatchState, incoming: MatchState) {
  return incoming.moveCount >= current.moveCount ? incoming : current;
}

/**
 * Loads a match, then follows it on its topic. The page only shows what the server sent, so
 * there is no optimistic update. The topic is joined after the load so a player who is not in
 * the match never subscribes to it.
 */
export function useMatch(matchId: string) {
  const { subscribe } = useSocket();
  const [load, setLoad] = useState<MatchLoad>({ phase: 'loading' });
  const [result, setResult] = useState<MatchFinalResult | null>(null);
  const [lastMove, setLastMove] = useState<unknown>(null);
  const [serverError, setServerError] = useState<ServerError | null>(null);
  const isReady = load.phase === 'ready';

  useEffect(() => {
    let active = true;
    getMatch(matchId)
      .then((match) => {
        if (!isMatchState(match)) {
          throw new Error('unexpected answer from the server');
        }
        if (active) {
          setLoad({ phase: 'ready', match });
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setLoad(describeFailure(error));
        }
      });
    return () => {
      active = false;
    };
  }, [matchId]);

  useEffect(() => {
    if (!isReady) {
      return;
    }
    const applyState = (incoming: MatchState) =>
      setLoad((current) =>
        current.phase === 'ready'
          ? { phase: 'ready', match: newest(current.match, incoming) }
          : current,
      );
    const stopTopic = subscribe(`/topic/matches/${matchId}`, (body) => {
      if (isFinalResult(body)) {
        setResult(body);
      } else if (isMoveEvent(body)) {
        applyState(body.match);
        setLastMove(body.move);
      } else if (isMatchState(body)) {
        applyState(body);
      }
    });
    const stopErrors = subscribe('/user/queue/errors', (body) => {
      if (isErrorMessage(body) && body.matchId === matchId) {
        setServerError((previous) => ({
          id: (previous?.id ?? 0) + 1,
          message: body.message,
        }));
      }
    });
    return () => {
      stopTopic();
      stopErrors();
    };
  }, [isReady, matchId, subscribe]);

  return {
    load,
    result,
    lastMove,
    serverError,
    clearServerError: () => setServerError(null),
  };
}
