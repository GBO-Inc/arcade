# Sprint 1 retrospective

Sprint: 26 October – 8 November 2026
Scrum Master: Zoltán Mester
Ticket: SCRUM-79

## Sprint goal

> By the end of this sprint anyone on the team can start the whole system with one command, register and log in.

Pinned in Discord #sprint-goal: TODO (date)

Result: **partly met.** The stack starts with one command (SCRUM-14) and the registration and login API works (SCRUM-16). The login and registration pages (SCRUM-17) are not built yet, so a user cannot register or log in through the UI.

## Planning

Every ticket had an owner (`owner-*` label and Jira assignee).

| Member | Tickets | Points |
|---|---|---|
| Zoltán | SCRUM-13, 14, 15, 16, 26, 79 | 16 |
| István | SCRUM-18, 19, 20, 21, 76 | 18 |
| Ozul | SCRUM-17, 22, 23, 24, 25 | 17 |
| **Total** | 16 tickets | **51** |

## Points done versus planned

| | Tickets | Points |
|---|---|---|
| Planned | 16 | 51 |
| Done by sprint end (8 November) | 4 | 11 |
| Done as of 14 November | 9 | 31 |

Done by 8 November: SCRUM-13, 14, 15, 18.
Finished after the end date: SCRUM-16, 22, 26 (10 November), SCRUM-20, 23 (11 November).

Burndown screenshot: TODO, save as `docs/retros/sprint-1-burndown.png`.

## Review

Shown live to the team on: TODO (date)

Demonstrated:
- One-command start with Docker Compose (SCRUM-14)
- CI on every pull request (SCRUM-15)
- Registration and login API with JWT (SCRUM-16)
- Frontend app shell and shared UI kit with `/ui-demo` (SCRUM-22, 23)
- Game engine contract with the dummy game (SCRUM-20)

Unfinished, carried over (moved to Sprint 2 on 14 November):

| Ticket | Owner | Points | Status | Reason |
|---|---|---|---|---|
| SCRUM-17 Login and registration pages | Ozul | 3 | To Do | Blocked by SCRUM-16 and 22 (done 10 November) and SCRUM-23 (done 11 November) |
| SCRUM-19 WebSocket endpoint with JWT | István | 5 | In Progress | Blocked by SCRUM-18 (done 8 November) and SCRUM-16 (done 10 November) |
| SCRUM-21 Test tooling and coverage in CI | István | 3 | In Progress | Blocked by SCRUM-15 and 18, both done on the last day (8 November) |
| SCRUM-24 API client and auth state | Ozul | 3 | In Progress | Blocked by SCRUM-16 and 22 (done 10 November) |
| SCRUM-25 Documentation skeleton | Ozul | 3 | To Do | Not blocked (SCRUM-13 and 14 done). TODO: ask Ozul |
| SCRUM-76 Shared contract types | István | 2 | To Do | Blocked by SCRUM-19, still in progress |
| SCRUM-79 This retrospective | Zoltán | 1 | To Do | Done with this document |

## Retrospective

Suggestions to discuss. Replace them with what the team agrees on.

### Keep
- CI must pass on every pull request before merge.
- The ticket's acceptance criteria are the specification, so reviews check against them.

### Stop
- Putting changes outside the ticket into a pull request. #8 also changed the local database and secret configuration, and these had to be reverted in review.
- Branch names outside the convention (`ARC-08-…`, `feature/ARC-10-…` instead of `feature/SCRUM-<n>-…`).

### Try
- Start and finish tickets that block others first. SCRUM-16, 18, 22 and 23 were done on or after the last day, and they held up SCRUM-17, 19, 21 and 24.
- Open smaller pull requests earlier. 5 of the 9 finished tickets were merged after the sprint end date.
- Ask for a review as soon as a pull request is open, not on the last day.
