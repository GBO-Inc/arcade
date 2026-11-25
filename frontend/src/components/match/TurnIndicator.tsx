import type { MatchState } from '../../games/matchTypes';

function turnText(match: MatchState, myPlayerId: string, opponentName: string) {
  if (match.status === 'WAITING') {
    return 'Waiting for the players to connect';
  }
  if (match.status !== 'IN_PROGRESS') {
    return 'Match over';
  }
  return match.currentTurnPlayerId === myPlayerId
    ? 'Your turn'
    : `${opponentName}'s turn`;
}

export function TurnIndicator({
  match,
  myPlayerId,
  opponentName,
}: {
  match: MatchState;
  myPlayerId: string;
  opponentName: string;
}) {
  return (
    <p
      data-testid="turn-indicator"
      aria-live="polite"
      className="text-center text-ui-lg font-semibold"
    >
      {turnText(match, myPlayerId, opponentName)}
    </p>
  );
}
