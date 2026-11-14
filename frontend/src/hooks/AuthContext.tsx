import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import {
  apiClient,
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  subscribeToUnauthorized,
} from '../api/client';
import { useNavigate } from 'react-router-dom';

export interface User {
  id?: string | number;
  email?: string;
  [key: string]: unknown;
}

interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
}

interface AuthContextValue {
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

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

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
