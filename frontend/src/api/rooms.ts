import { apiClient } from './client';

export type GameType = 'TIC_TAC_TOE' | 'CONNECT_FOUR' | 'CHECKERS';
export type RoomStatus = 'OPEN' | 'FULL' | 'STARTED' | 'CLOSED';

export const GAMES: { type: GameType; label: string }[] = [
  { type: 'TIC_TAC_TOE', label: 'Tic-Tac-Toe' },
  { type: 'CONNECT_FOUR', label: 'Connect Four' },
  { type: 'CHECKERS', label: 'Checkers' },
];

export interface Room {
  id: number;
  code: string;
  gameType: GameType;
  status: RoomStatus;
  creatorName: string;
  capacity: number;
  freeSeats: number;
}

export interface CreateRoomRequest {
  gameType: GameType;
}

export interface JoinRoomRequest {
  code: string;
}

export const ROOM_CODE_PATTERN = /^[A-HJ-NP-Z2-9]{6}$/;

export function listRooms() {
  return apiClient.get<Room[]>('/api/rooms');
}

export function createRoom(gameType: GameType) {
  const request: CreateRoomRequest = { gameType };
  return apiClient.post<Room>('/api/rooms', request);
}

export function joinRoom(code: string) {
  const request: JoinRoomRequest = { code };
  return apiClient.post<Room>('/api/rooms/join', request);
}

export interface RoomSeat {
  userId: number;
  displayName: string;
  avatarColour: string | null;
  ready: boolean;
}

/** The waiting room's view of a room. `matchId` is set once the room has started. */
export interface RoomDetail {
  id: number;
  code: string;
  gameType: GameType;
  status: RoomStatus;
  creatorId: number;
  capacity: number;
  matchId: string | null;
  seats: RoomSeat[];
}

export type RoomEvent =
  | { type: 'ROOM_UPDATED'; room: RoomDetail }
  | { type: 'MATCH_STARTED'; matchId: string }
  | { type: 'ROOM_CLOSED' };

/** The typed event in a `/topic/rooms/{id}` message, or null for anything else. */
export function parseRoomEvent(body: unknown): RoomEvent | null {
  if (typeof body !== 'object' || body === null || !('type' in body)) {
    return null;
  }
  if (body.type === 'ROOM_CLOSED') {
    return { type: 'ROOM_CLOSED' };
  }
  if (
    body.type === 'MATCH_STARTED' &&
    'matchId' in body &&
    typeof body.matchId === 'string'
  ) {
    return { type: 'MATCH_STARTED', matchId: body.matchId };
  }
  if (
    body.type === 'ROOM_UPDATED' &&
    'room' in body &&
    typeof body.room === 'object' &&
    body.room !== null
  ) {
    return { type: 'ROOM_UPDATED', room: body.room as RoomDetail };
  }
  return null;
}

export function getRoom(roomId: number | string) {
  return apiClient.get<RoomDetail>(`/api/rooms/${roomId}`);
}

export function setReady(roomId: number | string, ready: boolean) {
  return apiClient.post<RoomDetail>(`/api/rooms/${roomId}/ready`, { ready });
}

export function leaveRoom(roomId: number | string) {
  return apiClient.post<void>(`/api/rooms/${roomId}/leave`);
}

export function closeRoom(roomId: number | string) {
  return apiClient.post<void>(`/api/rooms/${roomId}/close`);
}
