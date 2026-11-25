# Realtime WebSocket (STOMP) Contract

> **Governance:** Changes to this contract require updating `docs/realtime-contract.md` and `frontend/src/contract/` in the same PR and approval from all three team members before merging.

## 1. Connection & Authentication

- **WebSocket Endpoint:** `ws://<host>/ws`
- **Protocol:** STOMP over WebSocket
- **Authentication:** Clients must send a valid JWT in the STOMP `CONNECT` frame:
  ```text
  Authorization: Bearer <jwt>
  ```
- **Origin Policy:** Handshakes are only accepted from origins listed in `arcade.websocket.allowed-origins`.

### Allowed Destinations
| Direction | Prefix | Description |
| --- | --- | --- |
| Client -> Server | `/app/**` | Routed to server `@MessageMapping` handlers. Direct sends to `/topic/**` or `/queue/**` are rejected. |
| Server -> Clients | `/topic/**` | Broadcast messages delivered to all subscribers of the topic. |
| Server -> Client | `/user/queue/**` | Private messages delivered only to the authenticated user's session. Raw `/queue/**` and `/user/{name}/**` subscriptions are rejected. |

### Message Summary
| Message | Destination | Direction | Payload Type |
| --- | --- | --- | --- |
| Echo request | `/app/echo` | Client -> Server | `EchoRequest` |
| Echo response | `/user/queue/echo` | Server -> Client | `EchoResponse` |
| Room event | `/topic/rooms/{code}` | Server -> Client | `RoomEvent` |
| Match state | `/topic/matches/{matchId}` | Server -> Client | `MatchStateResponse` |
| Submit move | `/app/match/{id}/move` | Client -> Server | `MatchMoveRequest` |
| Move applied broadcast | `/topic/matches/{matchId}` | Server -> Client | `MatchMoveEvent` |
| Match final result | `/topic/matches/{matchId}`, `/user/queue/match-result` | Server -> Client | `MatchFinalResult` |
| Error | `/user/queue/errors` | Server -> Client | `ErrorMessage` |
| Send chat | `/app/match/{id}/chat` | Client -> Server | `ChatSendRequest` |
| Chat broadcast | `/topic/matches/{matchId}/chat` | Server -> Client | `ChatMessage` |
| Clock update | `/topic/matches/{matchId}/clock` | Server -> Client | `ClockUpdate` |
| Room list invalidation | `/topic/rooms` | Server -> Client | `RoomListChangedEvent` |
---

## 2. Echo

### 2.1 Echo Request (Client -> Server)
- **Destination:** `/app/echo`
- **Payload (`EchoRequest`):**

<!-- type: EchoRequest -->
```json
{
  "message": "ping"
}
```

### 2.2 Echo Response (Server -> Client)
- **Destination:** `/user/queue/echo`
- **Payload (`EchoResponse`):**

<!-- type: EchoResponse -->
```json
{
  "message": "ping",
  "sender": "player-1",
  "timestamp": "2026-11-25T14:00:00Z"
}
```

---

## 3. Room Events

### 3.1 Room Event Broadcast (Server -> Room Subscribers)
- **Destination:** `/topic/rooms/{code}`
- **Payload (`RoomEvent`):**

<!-- type: RoomEvent -->
```json
{
  "type": "PLAYER_JOINED",
  "roomCode": "ABC123",
  "gameType": "TIC_TAC_TOE",
  "players": ["player-1", "player-2"],
  "matchId": null
}
```

---

## 4. Match State & Move Pipeline

### 4.1 Match State Snapshot (Server -> Both Players)
- **Destination:** `/topic/matches/{matchId}`
- **Trigger:** Published when a match starts or when full state is broadcast.
- **Payload (`MatchStateResponse`):**

<!-- type: MatchStateResponse -->
```json
{
  "matchId": "550e8400-e29b-41d4-a716-446655440000",
  "roomId": 42,
  "gameType": "TIC_TAC_TOE",
  "status": "IN_PROGRESS",
  "players": ["player-1", "player-2"],
  "currentTurnPlayerId": "player-1",
  "state": {
    "board": [
      [null, null, null],
      [null, null, null],
      [null, null, null]
    ],
    "xPlayer": "player-1",
    "oPlayer": "player-2",
    "currentPlayer": "player-1"
  },
  "moveCount": 0
}
```

---

### 4.2 Submit a Move (Client -> Server)
- **Destination:** `/app/match/{id}/move`
- **Path Variable:** `id` — Match UUID.
- **Payload (`MatchMoveRequest`):** Game-specific JSON move object (`TicTacToeMove` for `TIC_TAC_TOE`, `DummyMove` for `CONNECT_FOUR` / `CHECKERS`).

**Server Processing Order:**
1. Authenticate the STOMP session (`Principal`).
2. Verify the match exists and deserialize the move for its game type.
3. Verify the authenticated player belongs to the match.
4. Verify the match is not finished and is `IN_PROGRESS`.
5. Verify it is the player's turn.
6. Validate and apply the move via the game's `GameEngine`.
7. Broadcast the updated state + applied move to both players. If the move ends the game, finalize the match and publish the final result.

**Move payload per game type:**

| `gameType` | Move | Example |
| --- | --- | --- |
| `TIC_TAC_TOE` | `TicTacToeMove`: zero-based `row` and `col` (0-2) | `{"row": 1, "col": 2}` |
| `CONNECT_FOUR`, `CHECKERS` | `DummyMove` placeholder until their engines exist | `{"playerId": "player-1"}` |
<!-- type: MatchMoveRequest -->
```json
{
  "row": 0,
  "col": 2
}
```

---

### 4.3 Move Applied Broadcast (Server -> Both Players)
- **Destination:** `/topic/matches/{matchId}`
- **Trigger:** Published after every valid move is applied.
- **Payload (`MatchMoveEvent`):**

<!-- type: MatchMoveEvent -->
```json
{
  "match": {
    "matchId": "550e8400-e29b-41d4-a716-446655440000",
    "roomId": 42,
    "gameType": "TIC_TAC_TOE",
    "status": "IN_PROGRESS",
    "players": ["player-1", "player-2"],
    "currentTurnPlayerId": "player-2",
    "state": {
      "board": [
        [null, null, "X"],
        [null, null, null],
        [null, null, null]
      ],
      "xPlayer": "player-1",
      "oPlayer": "player-2",
      "currentPlayer": "player-2"
    },
    "moveCount": 1
  },
  "move": {
    "row": 0,
    "col": 2
  },
  "movedBy": "player-1"
}
```

| Field | Type | Description |
| --- | --- | --- |
| `match` | `MatchStateResponse` | Full updated match snapshot after applying the move. |
| `move` | `MatchMoveRequest` | The move payload that was just applied. |
| `movedBy` | `string` | ID of the player who submitted the move. |

---

### 4.4 Match Final Result (Server -> Both Players)
- **Destinations:**
  - `/user/queue/match-result` (sent individually to each participant)
  - `/topic/matches/{matchId}` (broadcast to match subscribers)
- **Trigger:** Published at most once when a match reaches a terminal state (`FINISHED` or `ABANDONED`).
- **Payload (`MatchFinalResult`):**

<!-- type: MatchFinalResult -->
```json
{
  "matchId": "550e8400-e29b-41d4-a716-446655440000",
  "roomId": 42,
  "gameType": "TIC_TAC_TOE",
  "players": ["player-1", "player-2"],
  "outcome": "WIN",
  "winnerId": "player-1",
  "reason": "Game completed"
}
```

| Field | Type | Description |
| --- | --- | --- |
| `outcome` | `string` | One of `WIN`, `DRAW`, `ABANDONED`. |
| `winnerId` | `string \| null` | Winner's player ID when `outcome` is `WIN`; otherwise `null`. |
| `reason` | `string` | Human-readable reason for match termination. |

## 5. Lobby Room List Invalidation

When a room is created, joined, or left successfully, the server broadcasts an invalidation
event. Clients should refetch `GET /api/rooms` to obtain the authoritative list of open rooms.

- **Destination:** `/topic/rooms`
- **Payload (`RoomListChangedEvent`):**

<!-- type: RoomListChangedEvent -->
```json
{
  "type": "ROOM_LIST_CHANGED"
}
```

The event is broadcast after the corresponding room operation succeeds. It contains no room
snapshot; clients use the REST list endpoint for current room data.

---

## 6. Error Handling (Server -> Sender Only)

When a move is rejected, **the match state remains unchanged**, no broadcast is sent to `/topic/matches/{matchId}`, and a private error message is sent **only to the sender**.

- **Destination:** `/user/queue/errors`
- **Payload (`ErrorMessage`):**

<!-- type: ErrorMessage -->
```json
{
  "code": "NOT_YOUR_TURN",
  "message": "Not your turn",
  "matchId": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Type | Description |
| --- | --- | --- |
| `code` | `string` | Machine-readable error code. |
| `message` | `string` | Human-readable error description. |
| `matchId` | `UUID \| null` | The match the rejected move was sent to, if applicable. |

### Error Codes
| `code` | Trigger |
| --- | --- |
| `MATCH_NOT_FOUND` | No live or finished match exists with the given ID. |
| `NOT_PARTICIPANT` | Authenticated user is not one of the two players in the match. |
| `MATCH_NOT_IN_PROGRESS` | Match is still `WAITING` for both players to connect. |
| `MATCH_FINISHED` | Match is already `FINISHED` or `ABANDONED`. |
| `NOT_YOUR_TURN` | Move was submitted by a participant when it is the opponent's turn. |
| `INVALID_MOVE` | Move payload is missing/malformed or rejected by the `GameEngine`. |

---

## 7. Chat

### 7.1 Send Chat Message (Client -> Server)
- **Destination:** `/app/match/{id}/chat`
- **Payload (`ChatSendRequest`):**

<!-- type: ChatSendRequest -->
```json
{
  "text": "Good luck!"
}
```

### 7.2 Chat Message Broadcast (Server -> Both Players)
- **Destination:** `/topic/matches/{matchId}/chat`
- **Payload (`ChatMessage`):**

<!-- type: ChatMessage -->
```json
{
  "matchId": "550e8400-e29b-41d4-a716-446655440000",
  "sender": "player-1",
  "text": "Good luck!",
  "sentAt": "2026-11-25T14:01:00Z"
}
```

---

## 8. Clock

### 8.1 Clock Update (Server -> Both Players)
- **Destination:** `/topic/matches/{matchId}/clock`
- **Payload (`ClockUpdate`):**

<!-- type: ClockUpdate -->
```json
{
  "matchId": "550e8400-e29b-41d4-a716-446655440000",
  "activePlayer": "player-1",
  "remainingMs": {
    "player-1": 295000,
    "player-2": 300000
  },
  "serverTime": "2026-11-25T14:01:05Z"
}
```
