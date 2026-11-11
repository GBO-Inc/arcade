import { useParams } from 'react-router-dom';
import { Button, TextField } from '../components/ui';

export function LoginPage() {
  return (
    <form
      className="mx-auto max-w-sm space-y-ui-4"
      onSubmit={(event) => event.preventDefault()}
    >
      <h1 className="text-ui-xl font-bold">Login</h1>
      <TextField
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        required
      />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        required
      />
      <Button type="submit">Sign in</Button>
    </form>
  );
}

export function RegisterPage() {
  return (
    <div>
      <h1 className="text-xl font-bold">Register</h1>
    </div>
  );
}

export function LobbyPage() {
  return (
    <div>
      <h1 className="text-xl font-bold">Lobby</h1>
    </div>
  );
}

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

export function ProfilePage() {
  return (
    <div>
      <h1 className="text-xl font-bold">Profile</h1>
    </div>
  );
}
