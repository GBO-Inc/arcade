import { useEffect, useState } from 'react';
import { getProfile, type Profile } from '../api/profile';

/**
 * Profiles by player id. A player whose profile cannot be loaded is missing from the result,
 * and the match page then shows a generic name instead of failing.
 */
export function usePlayerProfiles(playerIds: string[]) {
  const [profiles, setProfiles] = useState<Record<string, Profile>>({});
  // A string key, so a new array with the same ids does not refetch.
  const key = playerIds.join(',');

  useEffect(() => {
    let active = true;
    key.split(',').forEach((playerId) => {
      getProfile(playerId)
        .then((profile) => {
          if (active) {
            setProfiles((previous) => ({ ...previous, [playerId]: profile }));
          }
        })
        .catch(() => undefined);
    });
    return () => {
      active = false;
    };
  }, [key]);

  return profiles;
}
