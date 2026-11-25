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

**Example Payload (`DummyMove` — currently used for `TIC_TAC_TOE`):**
```json
{
  "playerId": "player-1"
}
```

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
      "scores": {
        "player-1": 1,
        "player-2": 0
      },
      "currentPlayer": "player-2",
      "waitingPlayer": "player-1"
    },
    "moveCount": 1
  },
  "move": {
    "playerId": "player-1"
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

---

## 3. Error Handling (Server -> Sender Only)

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
