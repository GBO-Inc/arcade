import { useParams, Link } from 'react-router-dom';

export function LoginPage() {
    return (
        <div className="max-w-md mx-auto mt-12 p-6 bg-slate-900 rounded-xl border border-slate-800">
            <h1 className="text-2xl font-bold mb-2">Sign In / Register</h1>
            <p className="text-slate-400 mb-4">Placeholder for Login & Registration (ARC-05).</p>
            <Link to="/lobby" className="text-indigo-400 hover:underline text-sm">
                → Continue to Lobby
            </Link>
        </div>
    );
}

export function LobbyPage() {
    return (
        <div>
            <h1 className="text-2xl font-bold mb-2">Lobby</h1>
            <p className="text-slate-400">Empty lobby placeholder. Room list will be added in Sprint 2 (ARC-16).</p>
        </div>
    );
}

export function MatchPage() {
    const { roomId} = useParams();
    return (
        <div>
            <h1 className="text-2xl font-bold mb-2">Match Room: {roomId}</h1>
            <p className="text-slate-400">Placeholder for Match Page Frame & Waiting Room (ARC-23, ARC-25).</p>
        </div>
    );
}

export function LeaderboardPage() {
    return (
        <div>
            <h1 className="text-2xl font-bold mb-2">Leaderboard</h1>
            <p className="text-slate-400">Placeholder for ELO ratings and leaderboard (ARC-40).</p>
        </div>
    );
}

export function HistoryPage() {
    return (
        <div>
            <h1 className="text-2xl font-bold mb-2">Match History</h1>
            <p className="text-slate-400">Placeholder for past matches (ARC-28).</p>
        </div>
    );
}

export function ProfilePage() {
    return (
        <div>
            <h1 className="text-2xl font-bold mb-2">Player Profile</h1>
            <p className="text-slate-400">Placeholder for display name and avatar settings (ARC-17).</p>
        </div>
    );
}