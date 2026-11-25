import type { ComponentType } from 'react';
import { GAME_LABELS, type GameType, type MatchState } from './matchTypes';

export interface BoardProps {
  match: MatchState;
  myPlayerId: string;
  /** Sends the game-specific move to the server; the board never changes its own state. */
  onMove: (move: unknown) => void;
}

// SCRUM-47 replaces this with the real registry.
const boards: Partial<Record<GameType, ComponentType<BoardProps>>> = {};

/** Renders the board registered for the match's game type, or a placeholder. */
export function BoardSlot(props: BoardProps) {
  const { gameType } = props.match;
  const Board = boards[gameType];
  if (!Board) {
    return (
      <p className="py-ui-8 text-center text-ui-muted">
        Board for {GAME_LABELS[gameType]} coming soon
      </p>
    );
  }
  return <Board {...props} />;
}
