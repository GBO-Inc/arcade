import { useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Button, TextField } from '../components/ui';
import { useAuth } from '../hooks/auth';

export function LoginPage() {
  const { login } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  const from = (
    location.state as {
      from?: { pathname?: string; search?: string; hash?: string };
    } | null
  )?.from;

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    const formData = new FormData(event.currentTarget);
    try {
      await login(
        String(formData.get('email')),
        String(formData.get('password')),
      );
      navigate(
        from
          ? `${from.pathname ?? '/'}${from.search ?? ''}${from.hash ?? ''}`
          : '/lobby',
        { replace: true },
      );
    } catch (loginError) {
      setError(
        loginError instanceof Error ? loginError.message : 'Unable to sign in',
      );
    }
  }

  return (
    <form className="mx-auto max-w-sm space-y-ui-4" onSubmit={handleSubmit}>
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
      {error && <p role="alert">{error}</p>}
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
