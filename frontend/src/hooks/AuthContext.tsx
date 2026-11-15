import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { AuthContext, type User } from './auth';
import {
  apiClient,
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeToUnauthorized,
} from '../api/client';

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(() => Boolean(getAccessToken()));

  // No navigation here: ProtectedRoute sends the user to /login and remembers the page they
  // were on, so they come back to it after signing in again.
  const logout = useCallback(() => {
    clearAccessToken();
    setUser(null);
    setIsLoading(false);
  }, []);

  useEffect(() => {
    const unsubscribe = subscribeToUnauthorized(logout);
    const token = getAccessToken();

    if (!token) {
      return unsubscribe;
    }

    let active = true;
    void apiClient
      .get<User>('/api/me')
      .then((currentUser) => {
        if (active) {
          setUser(currentUser);
        }
      })
      .catch(() => {
        if (active) {
          logout();
        }
      })
      .finally(() => {
        if (active) {
          setIsLoading(false);
        }
      });

    return () => {
      active = false;
      unsubscribe();
    };
  }, [logout]);

  const login = useCallback(async (username: string, password: string) => {
    setIsLoading(true);
    try {
      const { accessToken } = await apiClient.post<LoginResponse>(
        '/api/auth/login',
        { username, password },
      );
      setAccessToken(accessToken);
      const currentUser = await apiClient.get<User>('/api/me');
      setUser(currentUser);
    } finally {
      setIsLoading(false);
    }
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout, isLoading }}>
      {children}
    </AuthContext.Provider>
  );
}
