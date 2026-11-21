import { apiClient } from './client';

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

// Must match AvatarPalette.COLOURS on the backend, which rejects any other value.
export const AVATAR_PALETTE = [
  { hex: '#EF4444', name: 'Red' },
  { hex: '#F97316', name: 'Orange' },
  { hex: '#EAB308', name: 'Yellow' },
  { hex: '#22C55E', name: 'Green' },
  { hex: '#14B8A6', name: 'Teal' },
  { hex: '#3B82F6', name: 'Blue' },
  { hex: '#8B5CF6', name: 'Violet' },
  { hex: '#EC4899', name: 'Pink' },
] as const;

export function getProfile(userId: number | string) {
  return apiClient.get<Profile>(`/api/profile/${userId}`);
}

export function updateOwnProfile(update: ProfileUpdate) {
  return apiClient.put<Profile>('/api/profile', update);
}
