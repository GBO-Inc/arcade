import { useParams } from 'react-router-dom';

export function MatchPage() {
  const { id } = useParams();
  return (
    <div>
      <h1 className="text-xl font-bold">Match {id}</h1>
    </div>
  );
}

export function LeaderboardPage() {
  return (
    <div>
      <h1 className="text-xl font-bold">Leaderboard</h1>
    </div>
  );
}

export function HistoryPage() {
  return (
    <div>
      <h1 className="text-xl font-bold">History</h1>
    </div>
  );
}
