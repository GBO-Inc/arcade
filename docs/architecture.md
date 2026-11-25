# Architecture

Arcade is a monorepo with a React frontend and a Spring Boot backend.

## Components

```mermaid
flowchart LR
  Frontend["React 19 / TypeScript frontend"]
  Rest["REST API<br/>Auth, profile, rooms"]
  WebSocket["STOMP WebSocket<br/>/ws"]
  Match["Match service<br/>Match state machine"]
  Engines["Game engines<br/>pure move validation"]
  Database[("PostgreSQL 17<br/>Flyway migrations")]

  Frontend -->|VITE_API_BASE_URL or relative /api| Rest
  Frontend <-->|STOMP destinations| WebSocket
  Rest --> Match
  Rest --> Database
  WebSocket --> Match
  Match --> Engines
```

- The React frontend calls REST endpoints under `/api` for registration, login, the current user, rooms and profiles.
- Spring Security authenticates REST requests and STOMP channels. STOMP connections use `/ws`; clients send application messages below `/app`, subscribe to `/topic/**`, and receive private user messages below `/user/queue/**`.
- The lobby exposes room creation, listing and joining through `RoomController`.
- Successful room creation, joining, and leaving publish a room-list invalidation to
  `/topic/rooms`; clients refetch the open-room list through REST.
- `MatchService` owns match lifecycle and serialized state transitions. `MatchStateMachine` tracks waiting, in-progress, finished and abandoned states.
- Game engines implement the shared engine contract and receive state and moves as immutable Java records. Engines validate and apply moves without database, clock or network access.
- PostgreSQL stores Flyway-managed application data. Match state, disconnect handling and match-result publication are managed by the backend match components.

## One-move data flow

Clients send a move to `/app/match/{matchId}/move`. The message shapes and error codes are in [realtime-contract.md](realtime-contract.md).

```mermaid
sequenceDiagram
  participant C as Authenticated client
  participant WS as STOMP /ws
  participant W as MatchWebSocketController
  participant M as MatchService
  participant E as Registered game engine
  participant P as MatchEventPublisher

  C->>WS: SEND /app/match/{matchId}/move
  WS->>W: handleMove(matchId, payload, principal)
  W->>M: getMatchGameType(matchId)
  W->>W: Deserialize move for the game type
  W->>M: submitMove(matchId, player, move)
  M->>M: Lock match
  M->>M: Verify participant, status and turn
  M->>E: applyMove(current state, move)
  E-->>M: MoveResult
  alt Move rejected
    M-->>W: MatchException with code
    W-->>C: MatchErrorMessage to /user/queue/errors (sender only)
  else Move accepted
    M->>M: Record move and calculate outcome
    M->>P: publishMoveApplied(MatchMoveEvent)
    P-->>C: New state and move to /topic/matches/{matchId}
    opt Match finished
      M->>M: Finish match and claim result publication
      M->>P: publishMatchEnded(MatchFinalResult)
      P-->>C: Final result by private queue and match topic
    end
  end
```

The server is authoritative. A move is accepted only for an existing participant in an in-progress match whose turn it is. The engine receives the locked match state, rejects illegal moves and returns the next state. A rejected move leaves the state unchanged. Terminal outcomes are published once and the active match is removed from memory.
