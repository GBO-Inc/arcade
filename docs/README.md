# Documentation

This folder holds the project documents. Files are added by the tickets that produce them.

| Document | Created in | Purpose |
|---|---|---|
| `architecture.md` | SCRUM-25 | Components and the data flow of one move |
| `adr/` | SCRUM-20 | Architecture decision records |
| `realtime-contract.md` | SCRUM-76 | Every REST and WebSocket message |
| `checkers-rules.md` | SCRUM-43 | The agreed checkers rule set |
| `retros/` | SCRUM-79 | Sprint retrospective notes |
| `ai-usage.md` | SCRUM-25 | How AI assistance was used and checked |
| `wireframes/` | SCRUM-26 | Screen wireframes and shared UI decisions |

## How to add a game

Adding a new game to the arcade platform is straightforward and requires implementing the core generic contracts. Follow these steps:

### 1. Define the Game Models
Your state and move models must be standard Java Records to ensure they are easily serializable to JSON.
- Create a `State` record (e.g., `TicTacToeState`).
- Create a `Move` record (e.g., `TicTacToeMove`).

### 2. Implement the Game Engine
Create a pure class that implements the `GameEngine<YourState, YourMove, String>` interface:
- `initialState(List<String> players)`: Set up the starting board/state.
- `legalMoves(YourState state, String player)`: Return a list of valid moves for the given player.
- `applyMove(YourState state, YourMove move)`: Return a `MoveResult.success(newState)` if the move is valid, or `MoveResult.rejected("reason")` if it violates game rules.
- `result(YourState state)`: Check the board and return a `MatchResult`. Use `MatchResult.ongoing()`, `MatchResult.draw()`, or `MatchResult.win(winnerId)` so the server knows who won.

### 3. Testing
Write unit tests for your engine. The engine itself must reject illegal moves, including any move after `result(state)` is no longer `ONGOING`. `MatchStateMachine` only tracks the match lifecycle (`WAITING`, `IN_PROGRESS`, `FINISHED`, `ABANDONED`); it does not look at moves.
