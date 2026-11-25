export type GameType = 'TIC_TAC_TOE' | 'CONNECT_FOUR' | 'CHECKERS';

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
}

export interface Profile {
  id: number;
  displayName: string;
  avatarColour: string | null;
  createdAt: string;
}

export interface ProfileUpdate {
  displayName: string;
  avatarColour: string;
}

export type RoomStatus = 'OPEN' | 'FULL' | 'STARTED' | 'CLOSED';

export interface RoomSeat {
  userId: number;
  displayName: string;
  avatarColour: string | null;
  ready: boolean;
}

/** `GET /api/rooms/{id}` and the `room` of a `ROOM_UPDATED` event. */
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
