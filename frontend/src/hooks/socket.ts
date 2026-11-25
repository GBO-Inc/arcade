import { createContext, useContext } from 'react';
import type {
  MessageHandler,
  SocketStatus,
} from '../realtime/SocketConnection';

interface SocketContextValue {
  status: SocketStatus;
  /** Returns a function that cancels the subscription. It survives reconnects. */
  subscribe: (destination: string, handler: MessageHandler) => () => void;
  /** Returns false, and tells the user, when the message was not sent. */
  send: (destination: string, body: unknown) => boolean;
}

export const SocketContext = createContext<SocketContextValue | null>(null);

export function useSocket() {
  const context = useContext(SocketContext);
  if (!context) {
    throw new Error('useSocket must be used within a SocketProvider');
  }
  return context;
}
