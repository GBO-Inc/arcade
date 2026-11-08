# Wireframes (SCRUM-26)

Low-fidelity frames the three of us agree on before building screens. They are drawn as SVG so they render on GitHub and changes are reviewable in pull requests.

| Screen | Desktop | Phone (360 px) |
| --- | --- | --- |
| Login and register | [01-login-register.svg](01-login-register.svg) | – |
| Lobby | [02-lobby.svg](02-lobby.svg) | [03-lobby-phone.svg](03-lobby-phone.svg) |
| Waiting room | [04-waiting-room.svg](04-waiting-room.svg) | – |
| Match | [05-match.svg](05-match.svg) | [06-match-phone.svg](06-match-phone.svg) |
| Result dialog | [07-result-dialog.svg](07-result-dialog.svg) | – |
| Profile | [08-profile.svg](08-profile.svg) | – |
| Leaderboard | [09-leaderboard.svg](09-leaderboard.svg) | – |

## Match page regions

Desktop (1280 px):

- Turn indicator and timer: a top bar across the play area.
- Your player panel: left column, with the Resign button at the bottom.
- Board: centre column, a 520 px square slot.
- Opponent panel: right column, top, with a connected dot.
- Chat: right column, bottom, with the message list and input.

Phone (360 px):

- Opponent panel: thin bar at the top, with their timer.
- Turn indicator: pill under the opponent bar.
- Board: 328 px square in the middle.
- Your panel: thin bar under the board, with your timer.
- Bottom row: Resign and Chat (2) buttons.
- Chat opens as a bottom sheet over the board.

## Shared UI decisions

Colours (these match the app shell from SCRUM-22):

- Background: #020617 (Tailwind slate-950).
- Surface for header, cards and panels: #0f172a (slate-900). Header bottom border: #1e293b (slate-800).
- Control border for inputs and secondary buttons: #334155 (slate-700).
- Primary text: #f1f5f9 (slate-100). Secondary text: #94a3b8 (slate-400). Captions: #64748b.
- Accent for primary buttons, active tab, active nav and highlights: #4f46e5 (indigo-600), with white text.
- Success: #22c55e (green-500).
- Danger and errors: #ef4444 (red-500).
- Warning and low timer: #f59e0b (amber-500).

Font:

- Inter, with system-ui as the fallback.
- Sizes: 24 for page titles, 16 for body, 14 for small text, 11 for captions (uppercase, letter spacing 1). These map to Tailwind text-2xl, text-base, text-sm and text-xs.

Spacing:

- Tailwind's 4 px scale. Use 2, 4, 6 and 8 (8, 16, 24 and 32 px) for gaps and padding.
- Radius: 8 px for controls and 12 px for cards.
- Controls are 40 px tall.
- Content max width is 1152 px (max-w-6xl), with 64 px side margins on desktop.

## Review

Each member comments on or approves the pull request. Open questions go in PR comments.

Open questions:

- Piece colours on the match page are placeholders. Red and amber clash with the danger and low-timer colours, so each game should pick its own.
- The avatar colour set on the profile page (indigo, green, amber, red, cyan, purple) is a suggestion and is not part of the UI palette.
