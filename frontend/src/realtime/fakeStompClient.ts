import type { IFrame, IMessage, StompConfig } from '@stomp/stompjs';
import type { StompClient } from './SocketConnection';

// The connection only reads the headers of a frame, so tests build frames by hand.
function frame(headers: Record<string, string> = {}) {
  return { headers, body: '' } as IFrame;
}

/** Stands in for the STOMP client so tests can play the server's side of the socket. */
export class FakeStompClient implements StompClient {
  static instances: FakeStompClient[] = [];

  readonly subscriptions = new Map<string, Set<(message: IMessage) => void>>();
  readonly published: { destination: string; body: string }[] = [];
  activated = false;
  deactivated = false;

  constructor(readonly config: StompConfig) {
    FakeStompClient.instances.push(this);
  }

  static create(config: StompConfig) {
    return new FakeStompClient(config);
  }

  static reset() {
    FakeStompClient.instances = [];
  }

  static get latest() {
    return FakeStompClient.instances[FakeStompClient.instances.length - 1];
  }

  activate() {
    this.activated = true;
  }

  deactivate() {
    this.deactivated = true;
    return Promise.resolve();
  }

  subscribe(destination: string, callback: (message: IMessage) => void) {
    const callbacks = this.subscriptions.get(destination) ?? new Set();
    callbacks.add(callback);
    this.subscriptions.set(destination, callbacks);
    return {
      id: destination,
      unsubscribe: () => {
        callbacks.delete(callback);
      },
    };
  }

  publish(params: { destination: string; body?: string }) {
    this.published.push({
      destination: params.destination,
      body: params.body ?? '',
    });
  }

  connect() {
    this.config.onConnect?.(frame());
  }

  drop() {
    this.config.onWebSocketClose?.(new CloseEvent('close'));
  }

  reject(message = 'Invalid or expired JWT token') {
    this.config.onStompError?.(frame({ message }));
  }

  deliver(destination: string, payload: unknown) {
    const message = { body: JSON.stringify(payload) } as IMessage;
    this.subscriptions.get(destination)?.forEach((callback) => {
      callback(message);
    });
  }
}
