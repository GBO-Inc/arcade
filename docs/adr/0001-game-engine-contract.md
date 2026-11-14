# ADR 1: Game Engine Contract & Match State Machine

## Status
Accepted (approved by all 3 team members in PR #8, 11 Nov 2026)

## Context
We need a scalable way to integrate multiple turn-based games into the arcade platform. The platform must handle matchmaking, match lifecycle (ongoing, finished, abandoned), and move validation without hardcoding specific game rules into the core infrastructure.

## Alternatives Considered
1. **Tight Coupling:** Implementing game-specific logic directly inside the match orchestration. (Rejected: Not scalable, makes adding new games difficult and clutters the core logic).
2. **Generic Interfaces (Chosen):** Defining a strict, generic contract (`GameEngine<TState, TMove, TPlayer>`) that separates pure game logic from state persistence and match orchestration.

## Decision
We implemented a strict boundary between the pure game rules and the match lifecycle:
- The `GameEngine` interface is a pure component responsible for initial states, legal moves, applying moves, and determining match results.
- `MatchResult` is a generic record that clearly identifies the match status (`ONGOING`, `WIN`, `DRAW`) and the winner, ensuring the server can easily update leaderboards.
- `MatchStateMachine` acts as the orchestrator, holding the actual match lifecycle states (`WAITING`, `IN_PROGRESS`, `FINISHED`, `ABANDONED`) and rejecting invalid lifecycle transitions. Rejecting moves after the match is resolved is the engine's job (`applyMove` returns a rejection).
