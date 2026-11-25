import { useNavigate, useParams } from 'react-router-dom';
import { PlayerPanel } from '../components/match/PlayerPanel';
import { playerName } from '../components/match/playerName';
import { ResultDialog } from '../components/match/ResultDialog';
import { TurnIndicator } from '../components/match/TurnIndicator';
import { Toast } from '../components/ui';
import { BoardSlot } from '../games/boardRegistry';
import type { MatchState } from '../games/matchTypes';
import { useAuth } from '../hooks/auth';
import { useSocket } from '../hooks/socket';
import { useMatch } from '../hooks/useMatch';
import { usePlayerProfiles } from '../hooks/usePlayerProfiles';

export function MatchPage() {
  const { id } = useParams();
  const { user } = useAuth();
  if (!id || user?.id === undefined) {
    return null;
  }
  // The key gives each match a fresh component, so no state from the previous one leaks in.
  return <MatchView key={id} matchId={id} myPlayerId={String(user.id)} />;
}

function MatchView({
  matchId,
  myPlayerId,
}: {
  matchId: string;
  myPlayerId: string;
}) {
  const { load, result, serverError, clearServerError } = useMatch(matchId);
  const navigate = useNavigate();
  const players = load.phase === 'ready' ? load.match.players : [];
  const profiles = usePlayerProfiles(players);

  if (load.phase === 'loading') {
    return <p>Loading match…</p>;
  }
  if (load.phase === 'failed') {
    return (
      <div role="alert" className="space-y-ui-2">
        <h1 className="text-xl font-bold">
          {load.notFound ? 'Match not found' : 'Cannot open this match'}
        </h1>
        <p>{load.message}</p>
      </div>
    );
  }

  const { match } = load;
  const opponentId = match.players.find((id) => id !== myPlayerId) ?? '';
  return (
    <div className="mx-auto max-w-2xl space-y-ui-6">
      <div className="grid grid-cols-2 gap-ui-4">
        {match.players.map((playerId) => (
          <PlayerPanel
            key={playerId}
            playerId={playerId}
            profile={profiles[playerId]}
            isMe={playerId === myPlayerId}
            isTurn={match.currentTurnPlayerId === playerId}
          />
        ))}
      </div>
      <TurnIndicator
        match={match}
        myPlayerId={myPlayerId}
        opponentName={playerName(opponentId, profiles[opponentId])}
      />
      <Board match={match} myPlayerId={myPlayerId} />
      {serverError && (
        <div className="fixed bottom-4 right-4 z-50 max-w-sm">
          <Toast
            key={serverError.id}
            variant="error"
            message={serverError.message}
            onDismiss={clearServerError}
          />
        </div>
      )}
      {result && (
        <ResultDialog
          result={result}
          myPlayerId={myPlayerId}
          onBackToLobby={() => navigate('/lobby')}
        />
      )}
    </div>
  );
}

function Board({
  match,
  myPlayerId,
}: {
  match: MatchState;
  myPlayerId: string;
}) {
  const { send } = useSocket();
  return (
    <BoardSlot
      match={match}
      myPlayerId={myPlayerId}
      onMove={(move) => send(`/app/match/${match.matchId}/move`, move)}
    />
  );
}
