import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import {
    LoginPage,
    LobbyPage,
    MatchPage,
    LeaderboardPage,
    HistoryPage,
    ProfilePage,
} from './pages/Placeholders';

export default function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<AppLayout />}>
                    <Route index element={<Navigate to="/lobby" replace />} />
                    <Route path="login" element={<LoginPage />} />
                    <Route path="lobby" element={<LobbyPage />} />
                    <Route path="match/:roomId" element={<MatchPage />} />
                    <Route path="leaderboard" element={<LeaderboardPage />} />
                    <Route path="history" element={<HistoryPage />} />
                    <Route path="profile" element={<ProfilePage />} />
                    <Route path="*" element={<div className="text-slate-400">404 - Page not found</div>} />
                </Route>
            </Routes>
        </BrowserRouter>
    );
}