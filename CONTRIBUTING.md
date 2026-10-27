# Contributing to Arcade

The plan lives in Jira (project SCRUM). Every change starts from a ticket. The ticket holds the user story, the acceptance criteria and the dependencies, and the acceptance criteria are the specification.

## Workflow

1. Pick a ticket from the current sprint and read it, together with every ticket it depends on. If a dependency is not done, stop and say so on the ticket.
2. Create a branch from an up-to-date `main`: `feature/SCRUM-<n>-short-name`, for example `feature/SCRUM-33-tic-tac-toe-engine`.
3. Write the tests that express the acceptance criteria, then the code. Keep the change inside the ticket. New ideas become new backlog tickets.
4. Run lint, typecheck and tests locally.
5. Open a pull request using the template and link the ticket.
6. One teammate reviews and approves. The author merges after approval and green CI.

## Commit messages

Imperative, one line, ticket key first: `SCRUM-33 Add tic-tac-toe engine`.

## Architecture rules

- The server is the only authority on game state. Clients send moves, the server validates and broadcasts the result.
- Every game implements the same engine interface. Engines are pure: no database, clock or network access. Time and randomness are passed in.
- The REST and WebSocket message shapes are a shared contract (`docs/realtime-contract.md` and the frontend `contract` folder). A message change updates the document, both sides and the tests in the same pull request and needs approval from all three members.
- Database changes only through Flyway migrations. Never edit a migration that is already on `main`, add a new one.
- Secrets come from environment variables. Nothing secret is committed or written to logs, tickets or chat.

## Code style

- TypeScript strict mode, no `any` without a comment explaining why.
- Small functions, descriptive names, no dead code, no commented-out code.
- Java: constructor injection, immutable DTOs (records), no business logic in controllers.

## Definition of Done

A ticket is done only when all of these hold:

- Acceptance criteria are met and ticked off
- Tests are written and passing
- One teammate has approved the pull request
- CI is green: build, lint, typecheck and tests
- Documentation is updated where behaviour changed
- The pull request links the ticket
- The author can explain every part of the change in review

## Commands

The exact commands are added here as the setup tickets are completed (Docker Compose, CI, test tooling).

- Start the whole stack: `docker compose up --build` (first copy `.env.example` to `.env`; see the README section "Run locally")
- Stop it: `docker compose down`; reset the database: `docker compose down -v`
