# Realtime WebSocket (STOMP) Contract

> **Governance:** Changes to this contract require approval from all three team members before merging.

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

---

## 2. Match Move Pipeline

### 2.1 Submit a Move (Client -> Server)
- **Destination:** `/app/match/{id}/move`
- **Path Variable:** `id` — Match UUID.
- **Payload:** Game-specific JSON move object.

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

---

### 2.2 Move Applied Broadcast (Server -> Both Players)
- **Destination:** `/topic/matches/{matchId}`
- **Trigger:** Published after every valid move is applied.
- **Payload (`MatchMoveEvent`):**

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
        [null, null, null],
        [null, null, "X"],
        [null, null, null]
      ],
      "xPlayer": "player-1",
      "oPlayer": "player-2",
      "currentPlayer": "player-2"
    },
    "moveCount": 1
  },
  "move": {
    "row": 1,
    "col": 2
  },
  "movedBy": "player-1"
}
```

| Field | Type | Description |
| --- | --- | --- |
| `match` | `MatchStateResponse` | Full updated match snapshot after applying the move. |
| `move` | `Object` | The move payload that was just applied. |
| `movedBy` | `string` | ID of the player who submitted the move. |

---

### 2.3 Match Final Result (Server -> Both Players)
- **Destinations:**
  - `/user/queue/match-result` (sent individually to each participant)
  - `/topic/matches/{matchId}` (broadcast to match subscribers)
- **Trigger:** Published at most once when a match reaches a terminal state (`FINISHED` or `ABANDONED`).
- **Payload (`MatchFinalResult`):**

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

## 3. Lobby Room List Invalidation

When a room is created, joined, or left successfully, the server broadcasts an invalidation
event. Clients should refetch `GET /api/rooms` to obtain the authoritative list of open rooms.

- **Destination:** `/topic/rooms`
- **Payload (`RoomListChangedEvent`):**

```json
{
  "type": "ROOM_LIST_CHANGED"
}
```

The event is broadcast after the corresponding room operation succeeds. It contains no room
snapshot; clients use the REST list endpoint for current room data. Closing a room by its
creator publishes it too.

---

## 3a. Waiting Room

Players in a room wait for each other here. A match is created only when the room is full and
every player is ready; joining a room no longer creates one.

### REST

All requests need `Authorization: Bearer <jwt>`. Errors use the `ErrorResponse` body.

| Request | Response | Notes |
| --- | --- | --- |
| `GET /api/rooms/{id}` | `200` `RoomDetail` | Members only. The waiting room loads this first and again after a reconnect. |
| `POST /api/rooms/{id}/ready` with `{"ready": true \| false}` | `200` `RoomDetail` | Members only. `ready: true` needs a full room. |
| `POST /api/rooms/{id}/close` | `204` | Creator only. Empties and closes the room. |

`RoomDetail`:

```json
{
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
```

`seats` lists the players in join order; an empty seat is simply absent (`capacity - seats.length`).
`matchId` is `null` until the room has started (`status` `STARTED`), then holds the match UUID. A
client that missed `MATCH_STARTED`, for example after a reconnect, finds its match here.

| Status | Trigger |
| --- | --- |
| `400` | `ready` is missing. |
| `403` | `GET`: the user is not in the room. `close`: the user is not the creator. |
| `404` | The room does not exist or is closed. `ready`: the user is not in the room. |
| `409` | `ready: true` while the room is not full, or the room has already started. |

`POST /api/rooms/{id}/leave` now also sets every remaining player back to not ready.

### Room events (Server -> Room Members)
- **Destination:** `/topic/rooms/{id}`. Published only after the change is committed.
- **Payloads**, told apart by `type`:

| `type` | Extra fields | Trigger |
| --- | --- | --- |
| `ROOM_UPDATED` | `room`: `RoomDetail` | Someone joined, left or changed their ready state. |
| `MATCH_STARTED` | `matchId`: match UUID | The room was full and the last player became ready. Sent once. Players then open `/match/{matchId}`. |
| `ROOM_CLOSED` | none | The creator closed the room. |

Events can arrive slightly out of order across rapid changes; clients treat `ROOM_UPDATED` as a
snapshot and may refetch `GET /api/rooms/{id}` to be sure.

---

## 4. Error Handling (Server -> Sender Only)

When a move is rejected, **the match state remains unchanged**, no broadcast is sent to `/topic/matches/{matchId}`, and a private error message is sent **only to the sender**.

- **Destination:** `/user/queue/errors`
- **Payload (`MatchErrorMessage`):**

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
| `matchId` | `UUID` | The match the rejected move was sent to. |

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

## 5. REST: Load a Match

Used by the match page to load a match before it joins the topic, and to tell "unknown match" from "not your match".

- **Request:** `GET /api/matches/{id}` with `Authorization: Bearer <jwt>`. `id` is the match UUID.
- **Response `200`:** `MatchStateResponse`, the same shape as `match` in section 2.2.
- **Errors** (`ErrorResponse` body, `message` carries the text):

| Status | Trigger |
| --- | --- |
| `401` | Missing or invalid token. |
| `403` | The authenticated user is not one of the two players. |
| `404` | No live match with this ID. Finished matches leave memory, so their result is only delivered on the topic. |
