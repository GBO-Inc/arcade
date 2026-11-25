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
| Echo request | `/app/echo`, `/app/echo/private` | Client -> Server | `EchoRequest` |
| Echo response | `/topic/echo`, `/user/queue/echo` | Server -> Client | `EchoResponse` |
| Room event | `/topic/rooms/{id}` | Server -> Client | `RoomEvent` (`ROOM_UPDATED`, `MATCH_STARTED`, `ROOM_CLOSED`) |
| Match state | `/topic/matches/{matchId}` | Server -> Client | `MatchStateResponse` |
| Submit move | `/app/match/{id}/move` | Client -> Server | `MatchMoveRequest` |
| Move applied broadcast | `/topic/matches/{matchId}` | Server -> Client | `MatchMoveEvent` |
| Match final result | `/topic/matches/{matchId}`, `/user/queue/match-result` | Server -> Client | `MatchFinalResult` |
| Error | `/user/queue/errors` | Server -> Client | `ErrorMessage` |
| Send chat (planned, SCRUM-46) | `/app/match/{id}/chat` | Client -> Server | `ChatSendRequest` |
| Chat broadcast (planned, SCRUM-46) | `/topic/matches/{matchId}/chat` | Server -> Client | `ChatMessage` |
| Clock update (planned, SCRUM-56) | `/topic/matches/{matchId}/clock` | Server -> Client | `ClockUpdate` |
| Room list invalidation | `/topic/rooms` | Server -> Client | `RoomListChangedEvent` |
---

## 2. Echo

A connection check. `/app/echo` answers every subscriber of `/topic/echo`; `/app/echo/private`
answers only the sender on `/user/queue/echo`.

### 2.1 Echo Request (Client -> Server)
- **Destination:** `/app/echo` or `/app/echo/private`
- **Payload (`EchoRequest`):**

<!-- type: EchoRequest -->
```json
{
  "content": "ping"
}
```

### 2.2 Echo Response (Server -> Client)
- **Destination:** `/topic/echo` or `/user/queue/echo`
- **Payload (`EchoResponse`):** `sender` is the authenticated user id.

<!-- type: EchoResponse -->
```json
{
  "sender": "7",
  "content": "ping"
}
```

---

## 3. Waiting Room

Players in a room wait for each other here. A match is created only when the room is full and
every player is ready; joining a room does not create one.

### 3.1 REST

All requests need `Authorization: Bearer <jwt>`. Errors use the `ErrorResponse` body.

| Request | Response | Notes |
| --- | --- | --- |
| `GET /api/rooms/{id}` | `200` `RoomDetail` | Members only. The waiting room loads this first and again after a reconnect. |
| `POST /api/rooms/{id}/ready` with `{"ready": true \| false}` | `200` `RoomDetail` | Members only. `ready: true` needs a full room. |
| `POST /api/rooms/{id}/close` | `204` | Creator only. Empties and closes the room. |

`RoomDetail.seats` lists the players in join order; an empty seat is simply absent
(`capacity - seats.length`). `matchId` is `null` until the room has started (`status` `STARTED`),
then holds the match UUID, so a client that missed `MATCH_STARTED` still finds its match.

| Status | Trigger |
| --- | --- |
| `400` | `ready` is missing. |
| `403` | `GET`: the user is not in the room. `close`: the user is not the creator. |
| `404` | The room does not exist or is closed. `ready`: the user is not in the room. |
| `409` | `ready: true` while the room is not full, or the room has already started. |

`POST /api/rooms/{id}/leave` also sets every remaining player back to not ready.

### 3.2 Room Events (Server -> Room Members)
- **Destination:** `/topic/rooms/{id}`. Published only after the change is committed.
- **Payload (`RoomEvent`):** one of the three shapes below, told apart by `type`. Events can arrive
  slightly out of order across rapid changes; clients treat `ROOM_UPDATED` as a snapshot and may
  refetch `GET /api/rooms/{id}` to be sure.

`ROOM_UPDATED`: someone joined, left or changed their ready state. `room` is a `RoomDetail`.

<!-- type: RoomUpdatedEvent -->
```json
{
  "type": "ROOM_UPDATED",
  "room": {
    "id": 42,
    "code": "ABC234",
    "gameType": "TIC_TAC_TOE",
    "status": "FULL",
    "creatorId": 7,
    "capacity": 2,
    "matchId": null,
    "seats": [
      { "userId": 7, "displayName": "Alice", "avatarColour": "#EF4444", "ready": true },
      { "userId": 9, "displayName": "Bob", "avatarColour": null, "ready": false }
    ]
  }
}
```

`MATCH_STARTED`: the room was full and the last player became ready. Sent once; players then
open `/match/{matchId}`.

<!-- type: MatchStartedEvent -->
```json
{
  "type": "MATCH_STARTED",
  "matchId": "550e8400-e29b-41d4-a716-446655440000"
}
```

`ROOM_CLOSED`: the creator closed the room.

<!-- type: RoomClosedEvent -->
```json
{
  "type": "ROOM_CLOSED"
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
snapshot; clients use the REST list endpoint for current room data. Closing a room by its
creator publishes it too.

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

> **Planned (SCRUM-46).** Not implemented yet; this is the agreed shape.

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

> **Planned (SCRUM-56).** Not implemented yet; this is the agreed shape.

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

---

## 9. REST: Load a Match

Used by the match page to load a match before it joins the topic, and to tell "unknown match" from "not your match".

- **Request:** `GET /api/matches/{id}` with `Authorization: Bearer <jwt>`. `id` is the match UUID.
- **Response `200`:** `MatchStateResponse`, the same shape as in section 4.1.
- **Errors** (`ErrorResponse` body, `message` carries the text):

| Status | Trigger |
| --- | --- |
| `401` | Missing or invalid token. |
| `403` | The authenticated user is not one of the two players. |
| `404` | No live match with this ID. Finished matches leave memory, so their result is only delivered on the topic. |
