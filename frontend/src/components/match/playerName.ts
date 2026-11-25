import type { Profile } from '../../api/profile';

/** The profile name, or a generic one while the profile is missing. */
export function playerName(playerId: string, profile?: Profile) {
  return profile?.displayName ?? `Player ${playerId}`;
}
