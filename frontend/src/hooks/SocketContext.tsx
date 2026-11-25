import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { SocketContext } from './socket';
import { useAuth } from './auth';
import { getAccessToken } from '../api/client';
import { Toast } from '../components/ui';
import {
  SocketConnection,
  buildSocketUrl,
  type StompClient,
  type SocketStatus,
} from '../realtime/SocketConnection';
import type { StompConfig } from '@stomp/stompjs';

interface Notice {
  id: number;
  message: string;
}

export function SocketProvider({
  children,
  createClient,
}: {
  children: ReactNode;
  createClient?: (config: StompConfig) => StompClient;
}) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState<SocketStatus>('offline');
  const [notice, setNotice] = useState<Notice | null>(null);
  const noticeCount = useRef(0);

  // The connection is created once so subscriptions made by children, whose effects run before
  // this one, are not lost.
  const [connection] = useState(
    () =>
      new SocketConnection({
        url: buildSocketUrl(
          import.meta.env.VITE_API_BASE_URL || '',
          window.location,
        ),
        getToken: getAccessToken,
        onStatusChange: setStatus,
        createClient,
      }),
  );

  useEffect(
    () =>
      connection.onTokenRejected(() => {
        logout();
        navigate('/login');
      }),
    [connection, logout, navigate],
  );

  const isSignedIn = user !== null;
  useEffect(() => {
    if (!isSignedIn) {
      return;
    }
    connection.start();
    return () => connection.stop();
  }, [connection, isSignedIn]);

  const send = useCallback(
    (destination: string, body: unknown) => {
      const sent = connection.send(destination, body);
      if (!sent) {
        noticeCount.current += 1;
        setNotice({
          id: noticeCount.current,
          message: 'Move not sent: not connected to the server.',
        });
      }
      return sent;
    },
    [connection],
  );

  const value = useMemo(
    () => ({
      status,
      subscribe: connection.subscribe.bind(connection),
      send,
    }),
    [status, connection, send],
  );

  return (
    <SocketContext.Provider value={value}>
      {children}
      {notice && (
        <div className="fixed bottom-4 right-4 z-50 max-w-sm">
          <Toast
            key={notice.id}
            variant="error"
            message={notice.message}
            onDismiss={() => setNotice(null)}
          />
        </div>
      )}
    </SocketContext.Provider>
  );
}
