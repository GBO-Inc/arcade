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

## Testing rules & templates

- **Every ticket ships with tests.** No feature or refactor is complete without unit and/or integration tests covering its acceptance criteria.
- **Bug fixes start with a failing test.** Before fixing a bug, write an automated test that reproduces it and fails, then implement the fix until the test passes.
- **Naming conventions:**
  - Backend unit tests end with `*Test.java` (using JUnit 5, Mockito, and AssertJ) and run without Docker.
  - Backend integration tests end with `*IT.java`, extend [`AbstractIntegrationTest`](backend/src/test/java/com/arcade/backend/AbstractIntegrationTest.java) so a single PostgreSQL Testcontainer is shared across the entire test run, and verify database/HTTP/WebSocket behavior.
  - Frontend tests end with `*.test.ts` or `*.test.tsx` (using Vitest and React Testing Library).
- **Test templates (use these as starting points):**
  - Backend unit test template: [`AuthServiceTest.java`](backend/src/test/java/com/arcade/backend/auth/AuthServiceTest.java) (and [`MatchStateMachineTest.java`](backend/src/test/java/com/arcade/backend/match/MatchStateMachineTest.java))
  - Backend integration test template: [`FlywayMigrationIT.java`](backend/src/test/java/com/arcade/backend/FlywayMigrationIT.java)
  - Frontend component test template: [`ui.test.tsx`](frontend/src/components/ui/ui.test.tsx)

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
- Tests are written and passing (including a regression test for bug fixes)
- One teammate has approved the pull request
- CI is green: build, lint, typecheck, tests and coverage reports
- Documentation is updated where behaviour changed
- The pull request links the ticket
- The author can explain every part of the change in review

## Commands

The exact commands are added here as the setup tickets are completed (Docker Compose, CI, test tooling).

- Start the whole stack: `docker compose up --build` (first copy `.env.example` to `.env`; see the README section "Run locally")
- Stop it: `docker compose down`; reset the database: `docker compose down -v`
- Backend checks (in `backend/`):
  - `./gradlew test` runs unit tests (`*Test`)
  - `./gradlew integrationTest` runs Testcontainers integration tests (`*IT`)
  - `./gradlew build` compiles, runs all tests, generates the JaCoCo coverage report, and runs the Spotless format check
  - `./gradlew spotlessApply` fixes formatting
- Frontend checks (in `frontend/`): `npm run lint`, `npm run typecheck`, `npm test`, `npm run test:coverage`, `npm run build`

CI (`.github/workflows/ci.yml`) runs these checks on every pull request and on every push to `main`. The backend uses Gradle (Kotlin DSL) on Java 21; the frontend uses npm on Node 22.
