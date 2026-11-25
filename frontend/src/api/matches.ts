import type { MatchState } from '../games/matchTypes';
import { apiClient } from './client';

export function getMatch(matchId: string) {
  return apiClient.get<MatchState>(`/api/matches/${matchId}`);
}
