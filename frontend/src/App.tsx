import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { ProtectedRoute } from './components/layout/ProtectedRoute';
import { UiKitDemo } from './components/ui/UiKitDemo';
import { AuthProvider } from './hooks/AuthContext';
import { SocketProvider } from './hooks/SocketContext';
import {
  LoginPage,
  RegisterPage,
  MatchPage,
  LeaderboardPage,
  HistoryPage,
} from './pages/Placeholders';
import { ProfilePage } from './pages/ProfilePage';
import { LobbyPage } from './pages/LobbyPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <SocketProvider>
            <AppLayout />
          </SocketProvider>
        }
      >
        <Route index element={<Navigate to="/lobby" replace />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route
          path="lobby"
          element={
            <ProtectedRoute>
              <LobbyPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="match/:id"
          element={
            <ProtectedRoute>
              <MatchPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="leaderboard"
          element={
            <ProtectedRoute>
              <LeaderboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="history"
          element={
            <ProtectedRoute>
              <HistoryPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="profile"
          element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="profile/:userId"
          element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route path="ui-demo" element={<UiKitDemo />} />
        <Route path="*" element={<div>404 Not Found</div>} />
      </Route>
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
