import { useParams } from 'react-router-dom';

export function LoginPage() {
    return (
        <div>
            <h1 className="text-xl font-bold">Login</h1>
        </div>
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