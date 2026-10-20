# Arcade

Real-time two-player board games in the browser: tic-tac-toe, Connect Four and checkers, with a quiz duel as an optional extra.

Built by the GBO-Inc team (Game Boards Online): Zoltán, István and Ozul.

## Tech stack

- Frontend: React, TypeScript, Vite
- Backend: Java 21, Spring Boot (REST and STOMP over WebSocket)
- Database: PostgreSQL with Flyway migrations
- Tests: JUnit, Testcontainers, Vitest, Playwright
- Delivery: Docker Compose locally, GitHub Actions for CI

## Project structure

- `backend/` Spring Boot application
- `frontend/` React application
- `docs/` architecture notes, decisions and the realtime message contract

## Run locally

The setup is described here once the Docker Compose ticket is done.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
