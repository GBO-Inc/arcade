import type { ErrorMessage } from './error';
import type { GameType, RoomDetail } from './rest';

export type MatchStatus = 'WAITING' | 'IN_PROGRESS' | 'FINISHED' | 'ABANDONED';
export type MatchOutcome = 'WIN' | 'DRAW' | 'ABANDONED';

export interface EchoRequest {
  content: string;
}

export interface EchoResponse {
  sender: string;
  content: string;
}

export interface RoomUpdatedEvent {
  type: 'ROOM_UPDATED';
  room: RoomDetail;
}

export interface MatchStartedEvent {
  type: 'MATCH_STARTED';
  matchId: string;
}

export interface RoomClosedEvent {
  type: 'ROOM_CLOSED';
}

/** Everything sent on `/topic/rooms/{id}`, told apart by `type`. */
export type RoomEvent = RoomUpdatedEvent | MatchStartedEvent | RoomClosedEvent;

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
