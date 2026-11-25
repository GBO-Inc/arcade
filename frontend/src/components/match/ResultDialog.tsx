import type { MatchFinalResult } from '../../games/matchTypes';
import { Button, Dialog } from '../ui';

function title(result: MatchFinalResult, myPlayerId: string) {
  switch (result.outcome) {
    case 'DRAW':
      return 'Draw';
    case 'ABANDONED':
      return 'Match abandoned';
    case 'WIN':
      return result.winnerId === myPlayerId ? 'You won' : 'You lost';
  }
}

export function ResultDialog({
  result,
  myPlayerId,
  onBackToLobby,
}: {
  result: MatchFinalResult;
  myPlayerId: string;
  onBackToLobby: () => void;
}) {
  return (
    <Dialog open title={title(result, myPlayerId)} onClose={onBackToLobby}>
      <p className="mb-ui-6 text-ui-muted">{result.reason}</p>
      <Button onClick={onBackToLobby}>Back to lobby</Button>
    </Dialog>
  );
}
