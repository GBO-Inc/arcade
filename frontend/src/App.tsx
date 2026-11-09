import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import {
    LoginPage,
    RegisterPage,
    LobbyPage,
    MatchPage,
    LeaderboardPage,
    HistoryPage,
    ProfilePage,
} from './pages/Placeholders';

export function AppRoutes() {
    return (
        <Routes>
            <Route path="/" element={<AppLayout />}>
                <Route index element={<Navigate to="/lobby" replace />} />
                <Route path="login" element={<LoginPage />} />
                <Route path="register" element={<RegisterPage />} />
                <Route path="lobby" element={<LobbyPage />} />
                <Route path="match/:id" element={<MatchPage />} />
                <Route path="leaderboard" element={<LeaderboardPage />} />
                <Route path="history" element={<HistoryPage />} />
                <Route path="profile" element={<ProfilePage />} />
                <Route path="*" element={<div>404 Not Found</div>} />
            </Route>
        </Routes>
    );
}

export default function App() {
    return (
        <BrowserRouter>
            <AppRoutes />
        </BrowserRouter>
    );
}