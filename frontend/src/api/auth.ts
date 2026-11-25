import { apiClient } from './client';

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export function register(request: RegisterRequest) {
  return apiClient.post<unknown>('/api/auth/register', request);
}
