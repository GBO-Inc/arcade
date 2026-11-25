import {
  Client,
  type IMessage,
  type StompConfig,
  type StompSubscription,
} from '@stomp/stompjs';

export type SocketStatus =
  'connecting' | 'connected' | 'reconnecting' | 'offline';

/** The part of the STOMP client the connection uses, so tests can replace it. */
export interface StompClient {
  activate(): void;
  deactivate(): Promise<void>;
  subscribe(
    destination: string,
    callback: (message: IMessage) => void,
  ): StompSubscription;
  publish(params: { destination: string; body?: string }): void;
}

export type MessageHandler = (body: unknown) => void;

interface SocketConnectionOptions {
  url: string;
  getToken: () => string | null;
  onStatusChange: (status: SocketStatus) => void;
  createClient?: (config: StompConfig) => StompClient;
}

interface Subscription {
  destination: string;
  handler: MessageHandler;
  active?: StompSubscription;
}

const INITIAL_DELAY_MS = 1000;
const MAX_DELAY_MS = 15000;

export function buildSocketUrl(
  apiBaseUrl: string,
  location: Pick<Location, 'protocol' | 'host'>,
) {
  const origin = apiBaseUrl
    ? apiBaseUrl.replace(/\/$/, '')
    : `${location.protocol}//${location.host}`;
  return `${origin.replace(/^http/, 'ws')}/ws`;
}

function parseBody(body: string): unknown {
  try {
    return JSON.parse(body) as unknown;
  } catch {
    return body;
  }
}

/**
 * One STOMP connection that reconnects by itself.
 *
 * The STOMP client's own reconnectDelay is a fixed interval, so it is turned off and every
 * attempt gets a fresh client after an exponentially growing delay. Subscriptions are kept here
 * and replayed on every new client.
 */
export class SocketConnection {
  private readonly options: SocketConnectionOptions;
  private readonly subscriptions = new Set<Subscription>();
  private readonly rejectionHandlers = new Set<() => void>();
  private client: StompClient | null = null;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private attempt = 0;
  private running = false;
  private status: SocketStatus = 'offline';

  constructor(options: SocketConnectionOptions) {
    this.options = options;
  }

  start() {
    if (this.running) {
      return;
    }
    this.running = true;
    this.attempt = 0;
    this.setStatus('connecting');
    this.connect();
  }

  stop() {
    this.running = false;
    clearTimeout(this.timer);
    this.discardClient();
    this.setStatus('offline');
  }

  subscribe(destination: string, handler: MessageHandler) {
    const subscription: Subscription = { destination, handler };
    this.subscriptions.add(subscription);
    if (this.status === 'connected') {
      this.activate(subscription);
    }
    return () => {
      subscription.active?.unsubscribe();
      this.subscriptions.delete(subscription);
    };
  }

  /** Called once when the server refuses the token, after which the connection stays offline. */
  onTokenRejected(handler: () => void) {
    this.rejectionHandlers.add(handler);
    return () => {
      this.rejectionHandlers.delete(handler);
    };
  }

  /** Returns false when the message could not be sent because the socket is not connected. */
  send(destination: string, body: unknown) {
    if (this.status !== 'connected' || !this.client) {
      return false;
    }
    try {
      this.client.publish({ destination, body: JSON.stringify(body) });
      return true;
    } catch {
      return false;
    }
  }

  private connect() {
    const token = this.options.getToken();
    if (!token) {
      this.reject();
      return;
    }

    const create =
      this.options.createClient ?? ((config) => new Client(config));
    const client = create({
      brokerURL: this.options.url,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      onConnect: () => this.handleConnected(client),
      onStompError: () => this.handleStompError(client),
      onWebSocketClose: () => this.handleClosed(client),
    });
    this.client = client;
    client.activate();
  }

  private handleConnected(client: StompClient) {
    if (client !== this.client) {
      return;
    }
    this.attempt = 0;
    this.setStatus('connected');
    this.subscriptions.forEach((subscription) => this.activate(subscription));
  }

  // The server answers a refused CONNECT with an ERROR frame. Once connected, an ERROR frame
  // means something else (e.g. a refused subscription) and the token is not to blame.
  private handleStompError(client: StompClient) {
    if (client === this.client && this.status !== 'connected') {
      this.reject();
    }
  }

  private handleClosed(client: StompClient) {
    if (client !== this.client || !this.running) {
      return;
    }
    this.discardClient();
    this.setStatus('reconnecting');
    const delay = Math.min(INITIAL_DELAY_MS * 2 ** this.attempt, MAX_DELAY_MS);
    this.attempt += 1;
    this.timer = setTimeout(() => this.connect(), delay);
  }

  private reject() {
    this.running = false;
    clearTimeout(this.timer);
    this.discardClient();
    this.setStatus('offline');
    this.rejectionHandlers.forEach((handler) => handler());
  }

  private activate(subscription: Subscription) {
    subscription.active = this.client?.subscribe(
      subscription.destination,
      (message) => subscription.handler(parseBody(message.body)),
    );
  }

  private discardClient() {
    const client = this.client;
    this.client = null;
    this.subscriptions.forEach((subscription) => {
      subscription.active = undefined;
    });
    void client?.deactivate();
  }

  private setStatus(status: SocketStatus) {
    this.status = status;
    this.options.onStatusChange(status);
  }
}
