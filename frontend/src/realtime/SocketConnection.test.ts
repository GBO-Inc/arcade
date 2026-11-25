import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  SocketConnection,
  buildSocketUrl,
  type SocketStatus,
} from './SocketConnection';
import { FakeStompClient } from './fakeStompClient';

function setup(token: string | null = 'jwt-1') {
  const statuses: SocketStatus[] = [];
  const onTokenRejected = vi.fn();
  const connection = new SocketConnection({
    url: 'ws://test/ws',
    getToken: () => token,
    createClient: FakeStompClient.create,
    onStatusChange: (status) => statuses.push(status),
  });
  connection.onTokenRejected(onTokenRejected);
  return { connection, statuses, onTokenRejected };
}

describe('SocketConnection', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    FakeStompClient.reset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('connects with the stored token in the STOMP CONNECT headers', () => {
    const { connection, statuses } = setup('jwt-1');

    connection.start();
    expect(statuses).toEqual(['connecting']);
    expect(FakeStompClient.latest.activated).toBe(true);
    expect(FakeStompClient.latest.config.brokerURL).toBe('ws://test/ws');
    expect(FakeStompClient.latest.config.connectHeaders).toEqual({
      Authorization: 'Bearer jwt-1',
    });
    expect(FakeStompClient.latest.config.reconnectDelay).toBe(0);

    FakeStompClient.latest.connect();
    expect(statuses).toEqual(['connecting', 'connected']);
  });

  it('reconnects with a 1 s, 2 s, 4 s ... backoff capped at 15 s', () => {
    const { connection, statuses } = setup();
    connection.start();
    FakeStompClient.latest.connect();

    FakeStompClient.latest.drop();
    expect(statuses[statuses.length - 1]).toBe('reconnecting');

    for (const delay of [1000, 2000, 4000, 8000, 15000, 15000]) {
      const attempts = FakeStompClient.instances.length;
      vi.advanceTimersByTime(delay - 1);
      expect(FakeStompClient.instances).toHaveLength(attempts);
      vi.advanceTimersByTime(1);
      expect(FakeStompClient.instances).toHaveLength(attempts + 1);
      FakeStompClient.latest.drop();
    }
  });

  it('starts the backoff over after a successful reconnect', () => {
    const { connection, statuses } = setup();
    connection.start();
    FakeStompClient.latest.connect();
    FakeStompClient.latest.drop();
    vi.advanceTimersByTime(1000);
    FakeStompClient.latest.drop();
    vi.advanceTimersByTime(2000);
    FakeStompClient.latest.connect();
    expect(statuses[statuses.length - 1]).toBe('connected');

    FakeStompClient.latest.drop();
    const attempts = FakeStompClient.instances.length;
    vi.advanceTimersByTime(1000);
    expect(FakeStompClient.instances).toHaveLength(attempts + 1);
  });

  it('resubscribes to every topic after a reconnect and keeps delivering', () => {
    const { connection } = setup();
    const matchHandler = vi.fn();
    const errorHandler = vi.fn();
    connection.start();
    FakeStompClient.latest.connect();
    connection.subscribe('/topic/matches/1', matchHandler);
    connection.subscribe('/user/queue/errors', errorHandler);

    FakeStompClient.latest.drop();
    vi.advanceTimersByTime(1000);
    const reconnected = FakeStompClient.latest;
    expect(reconnected.subscriptions.size).toBe(0);
    reconnected.connect();

    expect([...reconnected.subscriptions.keys()]).toEqual([
      '/topic/matches/1',
      '/user/queue/errors',
    ]);
    reconnected.deliver('/topic/matches/1', { moveCount: 2 });
    reconnected.deliver('/user/queue/errors', { code: 'NOT_YOUR_TURN' });
    expect(matchHandler).toHaveBeenCalledWith({ moveCount: 2 });
    expect(errorHandler).toHaveBeenCalledWith({ code: 'NOT_YOUR_TURN' });
  });

  it('subscribes once connected when subscribe is called before the connection is up', () => {
    const { connection } = setup();
    const handler = vi.fn();
    connection.start();
    connection.subscribe('/topic/matches/1', handler);

    FakeStompClient.latest.connect();
    FakeStompClient.latest.deliver('/topic/matches/1', { ok: true });

    expect(handler).toHaveBeenCalledWith({ ok: true });
  });

  it('stops delivering and does not resubscribe after unsubscribe', () => {
    const { connection } = setup();
    const handler = vi.fn();
    connection.start();
    FakeStompClient.latest.connect();
    const unsubscribe = connection.subscribe('/topic/matches/1', handler);

    unsubscribe();
    FakeStompClient.latest.deliver('/topic/matches/1', { ok: true });
    FakeStompClient.latest.drop();
    vi.advanceTimersByTime(1000);
    FakeStompClient.latest.connect();

    expect(handler).not.toHaveBeenCalled();
    expect(FakeStompClient.latest.subscriptions.size).toBe(0);
  });

  it('sends JSON while connected', () => {
    const { connection } = setup();
    connection.start();
    FakeStompClient.latest.connect();

    expect(connection.send('/app/match/1/move', { playerId: 'p1' })).toBe(true);
    expect(FakeStompClient.latest.published).toEqual([
      {
        destination: '/app/match/1/move',
        body: JSON.stringify({ playerId: 'p1' }),
      },
    ]);
  });

  it('reports a failed send instead of dropping it silently while not connected', () => {
    const { connection } = setup();
    expect(connection.send('/app/match/1/move', {})).toBe(false);

    connection.start();
    expect(connection.send('/app/match/1/move', {})).toBe(false);

    FakeStompClient.latest.connect();
    FakeStompClient.latest.drop();
    expect(connection.send('/app/match/1/move', {})).toBe(false);
    expect(FakeStompClient.latest.published).toEqual([]);
  });

  it('stops retrying and reports the rejection when the server refuses the token', () => {
    const { connection, statuses, onTokenRejected } = setup();
    connection.start();

    FakeStompClient.latest.reject();
    FakeStompClient.latest.drop();
    vi.advanceTimersByTime(60_000);

    expect(onTokenRejected).toHaveBeenCalledTimes(1);
    expect(statuses[statuses.length - 1]).toBe('offline');
    expect(FakeStompClient.instances).toHaveLength(1);
    expect(FakeStompClient.latest.deactivated).toBe(true);
  });

  it('treats a rejection while reconnecting as final too', () => {
    const { connection, onTokenRejected } = setup();
    connection.start();
    FakeStompClient.latest.connect();
    FakeStompClient.latest.drop();
    vi.advanceTimersByTime(1000);

    FakeStompClient.latest.reject();
    vi.advanceTimersByTime(60_000);

    expect(onTokenRejected).toHaveBeenCalledTimes(1);
    expect(FakeStompClient.instances).toHaveLength(2);
  });

  it('does not connect without a stored token', () => {
    const { connection, statuses, onTokenRejected } = setup(null);

    connection.start();

    expect(FakeStompClient.instances).toHaveLength(0);
    expect(statuses[statuses.length - 1]).toBe('offline');
    expect(onTokenRejected).toHaveBeenCalledTimes(1);
  });

  it('goes offline and cancels a pending reconnect on stop', () => {
    const { connection, statuses } = setup();
    connection.start();
    FakeStompClient.latest.connect();
    FakeStompClient.latest.drop();

    connection.stop();
    vi.advanceTimersByTime(60_000);

    expect(statuses[statuses.length - 1]).toBe('offline');
    expect(FakeStompClient.instances).toHaveLength(1);
  });

  it('keeps its subscriptions across a stop and start', () => {
    const { connection } = setup();
    const handler = vi.fn();
    connection.subscribe('/topic/matches/1', handler);
    connection.start();
    connection.stop();
    connection.start();

    FakeStompClient.latest.connect();
    FakeStompClient.latest.deliver('/topic/matches/1', { ok: true });

    expect(handler).toHaveBeenCalledWith({ ok: true });
  });
});

describe('buildSocketUrl', () => {
  const location = { protocol: 'http:', host: 'localhost:5173' };

  it('uses the API base URL when it is set', () => {
    expect(buildSocketUrl('http://localhost:8080', location)).toBe(
      'ws://localhost:8080/ws',
    );
    expect(buildSocketUrl('https://api.example.com/', location)).toBe(
      'wss://api.example.com/ws',
    );
  });

  it('falls back to the page origin when no base URL is set', () => {
    expect(buildSocketUrl('', location)).toBe('ws://localhost:5173/ws');
    expect(
      buildSocketUrl('', { protocol: 'https:', host: 'arcade.example' }),
    ).toBe('wss://arcade.example/ws');
  });
});
