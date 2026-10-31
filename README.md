# Arcade

[![CI](https://github.com/GBO-Inc/arcade/actions/workflows/ci.yml/badge.svg)](https://github.com/GBO-Inc/arcade/actions/workflows/ci.yml)

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

You only need Docker (Docker Desktop on Windows and macOS).

```sh
cp .env.example .env      # once; on Windows PowerShell: Copy-Item .env.example .env
docker compose up --build
```

| Service    | URL / port              |
|------------|-------------------------|
| Frontend   | http://localhost:5173   |
| Backend    | http://localhost:8080   |
| PostgreSQL | localhost:5432 (user, password and database from `.env`) |

The frontend dev server reloads automatically when a file in `frontend/` changes.

```sh
docker compose down       # stop; database data is kept
docker compose down -v    # stop and delete the database volume (fresh start)
```

Run `docker compose up --build` again after changing a Dockerfile or `frontend/package.json`.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
