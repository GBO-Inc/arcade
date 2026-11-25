import type { ComponentType } from 'react';
import { GAME_LABELS, type GameType, type MatchState } from './matchTypes';
import { TicTacToeBoard } from './TicTacToeBoard';
export interface BoardProps {
  match: MatchState;
  myPlayerId: string;
  /** Sends the game-specific move to the server; the board never changes its own state. */
  onMove: (move: unknown) => void;
  /** The move the server confirmed last, in the game's own move shape, for highlighting. */
  lastMove?: unknown;
}

// SCRUM-47 replaces this with the real registry.
const boards: Partial<Record<GameType, ComponentType<BoardProps>>> = {
  TIC_TAC_TOE: TicTacToeBoard,
};

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
