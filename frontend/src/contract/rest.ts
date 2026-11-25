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
