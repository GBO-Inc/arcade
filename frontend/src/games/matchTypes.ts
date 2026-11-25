// Mirrors the match messages in docs/realtime-contract.md. SCRUM-76 moves these into the shared
// contract folder.

export type GameType = 'TIC_TAC_TOE' | 'CONNECT_FOUR' | 'CHECKERS';

export type MatchStatus = 'WAITING' | 'IN_PROGRESS' | 'FINISHED' | 'ABANDONED';

export type MatchOutcome = 'WIN' | 'DRAW' | 'ABANDONED';

/** `MatchStateResponse`: the REST answer for a match and the `match` of every move event. */
export interface MatchState {
  matchId: string;
  roomId: number;
  gameType: GameType;
  status: MatchStatus;
  players: string[];
  currentTurnPlayerId: string | null;
  /** Game-specific; only the game's own board component reads it. */
  state: unknown;
  moveCount: number;
}

export interface MatchMoveEvent {
  match: MatchState;
  move: unknown;
  movedBy: string;
}

export interface MatchFinalResult {
  matchId: string;
  roomId: number;
  gameType: GameType;
  players: string[];
  outcome: MatchOutcome;
  winnerId: string | null;
  reason: string;
}

export interface MatchErrorMessage {
  code: string;
  message: string;
  matchId: string;
}

export const GAME_LABELS: Record<GameType, string> = {
  TIC_TAC_TOE: 'Tic-tac-toe',
  CONNECT_FOUR: 'Connect four',
  CHECKERS: 'Checkers',
};

function isObject(body: unknown): body is Record<string, unknown> {
  return typeof body === 'object' && body !== null;
}

/** The match topic carries three shapes, told apart by their fields. */
export function isFinalResult(body: unknown): body is MatchFinalResult {
  return isObject(body) && typeof body.outcome === 'string';
}

export function isMoveEvent(body: unknown): body is MatchMoveEvent {
  return isObject(body) && isMatchState(body.match);
}

export function isMatchState(body: unknown): body is MatchState {
  return (
    isObject(body) &&
    typeof body.matchId === 'string' &&
    Array.isArray(body.players) &&
    typeof body.status === 'string'
  );
}

export function isErrorMessage(body: unknown): body is MatchErrorMessage {
  return (
    isObject(body) &&
    typeof body.code === 'string' &&
    typeof body.message === 'string'
  );
}
