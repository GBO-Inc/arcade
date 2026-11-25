// Plays one tic-tac-toe game through a running Arcade stack as the seeded users alice and bob.
//
// Usage:  node scripts/smoke-game.mjs
// Config: ARCADE_URL, default http://localhost:5173 (the Vite dev server, which proxies /api and /ws).
// Needs:  Node 22 or newer (global fetch and WebSocket). No npm packages.
//
// Start the stack with scripts/dev-reset.sh or scripts/dev-reset.ps1 first. A finished game frees
// both players, so the script can run again without a reset.

const BASE_URL = (process.env.ARCADE_URL ?? 'http://localhost:5173').replace(/\/+$/, '');
const PASSWORD = 'arcade-dev-password';
const TIMEOUT_MS = 60_000;
const POLL_MS = 50;
// The simple broker sends no RECEIPT for SUBSCRIBE, and it registers subscriptions on another
// thread, so a short pause lets them settle before anything is published to them.
const SUBSCRIBE_SETTLE_MS = 300;
const ERROR_QUEUE = '/user/queue/errors';
const RESET_HINT =
  'a player is still in a room from an earlier run. Run scripts/dev-reset.sh (or scripts/dev-reset.ps1) and try again.';

// Moves in play order. X is the first entry of the match's players list and moves first.
const MOVES = [
  ['X', 0, 0],
  ['O', 1, 0],
  ['X', 0, 1],
  ['O', 1, 1],
  ['X', 0, 2],
];

const deadline = Date.now() + TIMEOUT_MS;
let currentStep = 'start';

class SmokeError extends Error {}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function step(label, action) {
  currentStep = label;
  console.log(`> ${label}`);
  return action();
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

async function api(method, path, { token, body } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(10_000),
  });
  const text = await response.text();
  return { status: response.status, text, data: parseJson(text) };
}

function expectStatus(response, expected, label) {
  if (response.status !== expected) {
    throw new SmokeError(
      `${label}: expected HTTP ${expected}, got ${response.status} ${response.text.slice(0, 200)}`,
    );
  }
  return response.data;
}

async function login(username) {
  const tokens = expectStatus(
    await api('POST', '/api/auth/login', { body: { username, password: PASSWORD } }),
    200,
    `log in as ${username}`,
  );
  const me = expectStatus(
    await api('GET', '/api/me', { token: tokens.accessToken }),
    200,
    `load the profile of ${username}`,
  );
  return { name: username, token: tokens.accessToken, id: String(me.id) };
}

async function createRoom(alice) {
  const response = await api('POST', '/api/rooms', {
    token: alice.token,
    body: { gameType: 'TIC_TAC_TOE' },
  });
  if (response.status === 409) {
    throw new SmokeError(`alice cannot create a room: ${RESET_HINT}`);
  }
  return expectStatus(response, 201, 'alice creates a TIC_TAC_TOE room');
}

async function joinRoom(bob, code) {
  const response = await api('POST', '/api/rooms/join', { token: bob.token, body: { code } });
  if (response.status === 409) {
    throw new SmokeError(`bob cannot join the room: ${RESET_HINT}`);
  }
  expectStatus(response, 200, 'bob joins the room');
}

async function setReady(player, roomId) {
  const response = await api('POST', `/api/rooms/${roomId}/ready`, {
    token: player.token,
    body: { ready: true },
  });
  expectStatus(response, 200, `${player.name} marks ready`);
}

function parseFrame(text) {
  const headerEnd = text.indexOf('\n\n');
  const head = headerEnd === -1 ? text : text.slice(0, headerEnd);
  const body = headerEnd === -1 ? '' : text.slice(headerEnd + 2);
  const [command, ...headerLines] = head.split('\n');
  const headers = {};
  for (const line of headerLines) {
    const colon = line.indexOf(':');
    headers[line.slice(0, colon)] = line.slice(colon + 1);
  }
  return { command, headers, body };
}

// Minimal STOMP 1.2 client over the global WebSocket. Frames are text, NUL-terminated.
class StompClient {
  constructor(name, token) {
    this.name = name;
    this.token = token;
    this.socket = null;
    this.connected = false;
    this.nextSubscription = 0;
    this.subscriptions = new Map();
    this.inbox = [];
    this.failures = [];
  }

  connect() {
    const url = `${BASE_URL.replace(/^http/, 'ws')}/ws`;
    this.socket = new WebSocket(url);
    this.socket.onopen = () =>
      this.sendFrame('CONNECT', {
        'accept-version': '1.2',
        host: new URL(BASE_URL).host,
        Authorization: `Bearer ${this.token}`,
      });
    this.socket.onmessage = (event) => this.receive(String(event.data));
    this.socket.onerror = () => this.failures.push('WebSocket error');
    return this.waitUntil('STOMP CONNECTED frame', () => (this.connected ? true : undefined));
  }

  close() {
    this.socket?.close();
  }

  sendFrame(command, headers, body = '') {
    const lines = [command, ...Object.entries(headers).map(([key, value]) => `${key}:${value}`)];
    this.socket.send(`${lines.join('\n')}\n\n${body}\0`);
  }

  subscribe(destination) {
    const id = `sub-${this.nextSubscription++}`;
    this.subscriptions.set(id, destination);
    this.sendFrame('SUBSCRIBE', { id, destination });
    return sleep(SUBSCRIBE_SETTLE_MS);
  }

  sendMove(matchId, move) {
    this.sendFrame(
      'SEND',
      { destination: `/app/match/${matchId}/move`, 'content-type': 'application/json' },
      JSON.stringify(move),
    );
  }

  waitForMessage(description, destination, predicate) {
    return this.waitUntil(`${description} on ${destination}`, () => {
      const found = this.inbox.find((m) => m.destination === destination && predicate(m.body));
      return found?.body;
    });
  }

  receive(data) {
    for (const raw of data.split('\0')) {
      // Bare newlines are heartbeats between frames.
      const text = raw.replace(/^\n+/, '');
      if (text) this.handle(parseFrame(text));
    }
  }

  handle({ command, headers, body }) {
    if (command === 'CONNECTED') {
      this.connected = true;
    } else if (command === 'ERROR') {
      this.failures.push(`STOMP error: ${headers.message ?? body}`);
    } else if (command === 'MESSAGE') {
      const destination = this.subscriptions.get(headers.subscription);
      const payload = parseJson(body);
      if (destination === ERROR_QUEUE) {
        this.failures.push(`server rejected a move: ${payload.code} ${payload.message}`);
      }
      this.inbox.push({ destination, body: payload });
    }
  }

  async waitUntil(description, check) {
    for (;;) {
      if (this.failures.length > 0) {
        throw new SmokeError(`${this.name}: ${this.failures[0]}`);
      }
      const found = check();
      if (found !== undefined) return found;
      if (Date.now() > deadline) {
        throw new SmokeError(`${this.name}: timed out waiting for ${description}`);
      }
      await sleep(POLL_MS);
    }
  }
}

// Best effort after a failure, so a broken run does not leave both players stuck in the room. A room
// whose match already started cannot be left; the finished-match release takes care of that one.
async function leaveRoom(players, roomId) {
  await Promise.all(
    players.map((player) =>
      api('POST', `/api/rooms/${roomId}/leave`, { token: player.token }).catch(() => undefined),
    ),
  );
}

async function playGame(alice, bob, room) {
  const clients = [];
  try {
    const aliceWs = new StompClient(alice.name, alice.token);
    const bobWs = new StompClient(bob.name, bob.token);
    clients.push(aliceWs, bobWs);
    const clientById = new Map([
      [alice.id, aliceWs],
      [bob.id, bobWs],
    ]);

    await step('both players connect over STOMP', () =>
      Promise.all(clients.map((client) => client.connect())),
    );
    const roomTopic = `/topic/rooms/${room.id}`;
    await step(`both players subscribe to ${roomTopic}`, () =>
      Promise.all(clients.map((client) => client.subscribe(roomTopic))),
    );
    await step('alice marks ready', () => setReady(alice, room.id));
    await step('bob marks ready', () => setReady(bob, room.id));

    const started = await step('wait for MATCH_STARTED', () =>
      Promise.all(
        clients.map((client) =>
          client.waitForMessage('MATCH_STARTED', roomTopic, (m) => m.type === 'MATCH_STARTED'),
        ),
      ),
    );
    const matchId = started[0].matchId;
    const matchTopic = `/topic/matches/${matchId}`;
    console.log(`  match ${matchId}`);

    await step(`both players subscribe to ${matchTopic} and ${ERROR_QUEUE}`, () =>
      Promise.all(
        clients.flatMap((client) => [client.subscribe(matchTopic), client.subscribe(ERROR_QUEUE)]),
      ),
    );
    const snapshots = await step('wait for the match state snapshot', () =>
      Promise.all(
        clients.map((client) =>
          client.waitForMessage('IN_PROGRESS snapshot', matchTopic, (m) => m.status === 'IN_PROGRESS'),
        ),
      ),
    );

    const [xPlayer, oPlayer] = snapshots[0].players;
    const seats = { X: clientById.get(xPlayer), O: clientById.get(oPlayer) };
    if (!seats.X || !seats.O) {
      throw new SmokeError(`unexpected players in the snapshot: ${JSON.stringify(snapshots[0].players)}`);
    }
    console.log(`  X is ${seats.X.name} (player ${xPlayer}), O is ${seats.O.name} (player ${oPlayer})`);

    for (const [index, [symbol, row, col]] of MOVES.entries()) {
      const moveCount = index + 1;
      await step(`${symbol} (${seats[symbol].name}) plays row ${row}, col ${col}`, () => {
        seats[symbol].sendMove(matchId, { row, col });
        return Promise.all(
          clients.map((client) =>
            client.waitForMessage(`move ${moveCount} applied`, matchTopic, (m) => m.match?.moveCount === moveCount),
          ),
        );
      });
    }

    const results = await step('wait for the final result', () =>
      Promise.all(
        clients.map((client) =>
          client.waitForMessage('final result', matchTopic, (m) => m.outcome !== undefined),
        ),
      ),
    );
    const [result] = results;
    console.log(`  outcome ${result.outcome}, winner ${result.winnerId ?? 'none'}`);
    if (result.outcome !== 'WIN' || result.winnerId !== xPlayer) {
      throw new SmokeError(`expected X to win, got ${JSON.stringify(result)}`);
    }
  } finally {
    clients.forEach((client) => client.close());
  }
}

async function main() {
  const alice = await step('log in as alice', () => login('alice'));
  const bob = await step('log in as bob', () => login('bob'));
  const room = await step('alice creates a TIC_TAC_TOE room', () => createRoom(alice));
  console.log(`  room ${room.id}, code ${room.code}`);
  try {
    await step(`bob joins room ${room.code}`, () => joinRoom(bob, room.code));
    await playGame(alice, bob, room);
  } catch (error) {
    await leaveRoom([alice, bob], room.id);
    throw error;
  }
  console.log('Smoke test passed: a full tic-tac-toe game was played through the stack.');
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(`FAILED at step "${currentStep}": ${error.message}`);
    if (error.cause) console.error(`  cause: ${error.cause.message ?? error.cause}`);
    process.exit(1);
  });
