import { apiClient } from './client';

export type GameType = 'TIC_TAC_TOE' | 'CONNECT_FOUR' | 'CHECKERS';
export type RoomStatus = 'OPEN' | 'FULL' | 'STARTED' | 'CLOSED';

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
