import type { ErrorMessage } from './error';
import type { GameType } from './rest';

export type MatchStatus = 'WAITING' | 'IN_PROGRESS' | 'FINISHED' | 'ABANDONED';
export type MatchOutcome = 'WIN' | 'DRAW' | 'ABANDONED';
export type RoomEventType =
  'PLAYER_JOINED' | 'PLAYER_LEFT' | 'ROOM_UPDATED' | 'MATCH_STARTED';

export interface EchoRequest {
  message: string;
}

export interface EchoResponse {
  message: string;
  sender: string;
  timestamp: string;
}

export interface RoomEvent {
  type: RoomEventType;
  roomCode: string;
  gameType: GameType;
  players: string[];
  matchId: string | null;
}

export interface TicTacToeMove {
  row: number;
  col: number;
}

export interface DummyMove {
  playerId: string;
}

export type MatchMoveRequest = TicTacToeMove | DummyMove;

export interface TicTacToeState {
  board: (string | null)[][];
  xPlayer: string;
  oPlayer: string;
  currentPlayer: string;
}

export interface MatchStateResponse<TState = unknown> {
  matchId: string;
  roomId: number;
  gameType: GameType;
  status: MatchStatus;
  players: string[];
  currentTurnPlayerId: string;
  state: TState;
  moveCount: number;
}

export interface MatchMoveEvent<TMove = MatchMoveRequest, TState = unknown> {
  match: MatchStateResponse<TState>;
  move: TMove;
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

export interface ChatSendRequest {
  text: string;
}

export interface ChatMessage {
  matchId: string;
  sender: string;
  text: string;
  sentAt: string;
}

export interface ClockUpdate {
  matchId: string;
  activePlayer: string;
  remainingMs: Record<string, number>;
  serverTime: string;
}

export interface RoomListChangedEvent {
  type: 'ROOM_LIST_CHANGED';
}

/** Discriminated union of all server-to-client WebSocket messages. */
export type IncomingMessage =
  | { kind: 'echo'; payload: EchoResponse }
  | { kind: 'room_event'; payload: RoomEvent }
  | { kind: 'match_state'; payload: MatchStateResponse }
  | { kind: 'match_move'; payload: MatchMoveEvent }
  | { kind: 'match_result'; payload: MatchFinalResult }
  | { kind: 'error'; payload: ErrorMessage }
  | { kind: 'chat'; payload: ChatMessage }
  | { kind: 'clock'; payload: ClockUpdate }
  | { kind: 'room_list_changed'; payload: RoomListChangedEvent };
