import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { AuthContext, type User } from './auth';
import {
  apiClient,
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeToUnauthorized,
} from '../api/client';
import { useNavigate } from 'react-router-dom';

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(() => Boolean(getAccessToken()));

  const logout = useCallback(() => {
    clearAccessToken();
    setUser(null);
    setIsLoading(false);
    navigate('/login', { replace: true });
  }, [navigate]);

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
      .catch(() => undefined)
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

  const login = useCallback(async (email: string, password: string) => {
    setIsLoading(true);
    try {
      const { accessToken } = await apiClient.post<LoginResponse>(
        '/api/auth/login',
        { email, password },
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
