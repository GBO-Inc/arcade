# Contributing to Arcade

The plan lives in Jira (project SCRUM). Every change starts from a ticket. The ticket holds the user story, the acceptance criteria and the dependencies, and the acceptance criteria are the specification.

## Workflow

1. Pick a ticket from the current sprint and read it, together with every ticket it depends on. If a dependency is not done, stop and say so on the ticket.
2. Create a branch from an up-to-date `main`: `feature/SCRUM-<n>-short-name`, for example `feature/SCRUM-33-tic-tac-toe-engine`.
3. Write the tests that express the acceptance criteria, then the code. Keep the change inside the ticket. New ideas become new backlog tickets.
4. Run the relevant lint, typecheck and tests locally.
5. Open a pull request using the template and link the ticket.
6. One teammate reviews and approves. The author merges after approval and green CI.

## Commit messages

Use this format:

```text
SCRUM-<n> Imperative message
```

Examples:

```text
SCRUM-33 Add tic-tac-toe engine
SCRUM-25 Document project setup and architecture
```

## Pull requests and review

- Use `.github/pull_request_template.md`.
- Put `SCRUM-<n>` in the PR title or ticket field and link the ticket in the PR.
- Describe what changed and how it was tested.
- Delete UI screenshot sections when no UI changed.
- One teammate must approve the pull request.
- The reviewer checks correctness, acceptance criteria, tests, documentation, security and maintainability.
- The author resolves review feedback and can explain every part of the change during review.
- Do not merge until approval and CI are green.

## Testing rules and templates

- **Every ticket ships with tests.** No feature or refactor is complete without unit and/or integration tests covering its acceptance criteria.
- **Bug fixes start with a failing test.** Before fixing a bug, write an automated test that reproduces it and fails, then implement the fix until the test passes.
- **Backend unit tests** end with `*Test.java`, use JUnit 5, Mockito and AssertJ, and run without Docker.
- **Backend integration tests** end with `*IT.java`, extend [`AbstractIntegrationTest`](backend/src/test/java/com/arcade/backend/AbstractIntegrationTest.java), use a shared PostgreSQL Testcontainer and verify database, HTTP or WebSocket behavior. Docker must be running.
- **Frontend tests** end with `*.test.ts` or `*.test.tsx` and use Vitest and React Testing Library.
- Test templates:
  - [`AuthServiceTest.java`](backend/src/test/java/com/arcade/backend/auth/AuthServiceTest.java)
  - [`MatchStateMachineTest.java`](backend/src/test/java/com/arcade/backend/match/MatchStateMachineTest.java)
  - [`FlywayMigrationIT.java`](backend/src/test/java/com/arcade/backend/FlywayMigrationIT.java)
  - [`ui.test.tsx`](frontend/src/components/ui/ui.test.tsx)

## Architecture rules

- The server is the only authority on game state. Clients send moves, the server validates and broadcasts the result.
- Every game implements the same engine interface. Engines are pure: no database, clock or network access. Time and randomness are passed in.
- The REST and WebSocket message shapes are a shared contract (`docs/realtime-contract.md` and the frontend `contract` folder). A message change updates the document, both sides and the tests in the same pull request and needs approval from all three members.
- Database changes only through Flyway migrations. Never edit a migration that is already on `main`; add a new migration.
- Secrets come from environment variables. Nothing secret is committed or written to logs, tickets or chat.

## Code style

- TypeScript strict mode, no `any` without a comment explaining why.
- Small functions, descriptive names, no dead code and no commented-out code.
- Java uses constructor injection, immutable DTO records and no business logic in controllers.

## Local setup and checks

Create `.env` from `.env.example` before using Docker Compose:

```sh
cp .env.example .env
docker compose up --build
```

Set `JWT_SECRET` to at least 32 characters. Docker must be running for Testcontainers integration tests.

Frontend checks run in `frontend/`:

```sh
npm ci
npm run lint
npm run typecheck
npm test
npm run test:coverage
npm run build
```

Backend checks run in `backend/`:

```sh
./gradlew test
./gradlew integrationTest
./gradlew build
./gradlew spotlessApply
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

The backend build runs unit and integration tests, JaCoCo coverage and the Spotless check.

## Definition of Done

A ticket is done only when all of these hold:

1. All acceptance criteria are met and ticked off in the ticket.
2. Tests are written and passing.
3. One teammate has approved the pull request.
4. CI is green: build, lint, typecheck and tests.
5. Documentation is updated where behaviour changed.
6. The pull request is linked to the ticket.
7. The author can explain every part of the change in review.
