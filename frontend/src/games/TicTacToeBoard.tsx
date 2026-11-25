import type { MatchState } from './matchTypes';

export interface CellCoord {
  row: number;
  col: number;
}

export interface TicTacToeState {
  board: (string | null)[][];
  xPlayer?: string;
  oPlayer?: string;
  currentPlayer?: string;
  lastMove?: CellCoord | null;
  winningLine?: CellCoord[] | null;
}

export interface TicTacToeBoardProps {
  match: MatchState;
  myPlayerId: string;
  onMove: (move: unknown) => void;
  disabled?: boolean;
  lastMove?: CellCoord | null;
  winningLine?: CellCoord[] | null;
}

function isTicTacToeState(value: unknown): value is TicTacToeState {
  if (typeof value !== 'object' || value === null) return false;
  const record = value as { board?: unknown };
  return (
    Array.isArray(record.board) &&
    record.board.every((row) => Array.isArray(row))
  );
}

function isSameCell(
  cell: CellCoord | null | undefined,
  row: number,
  col: number,
): boolean {
  return Boolean(cell && cell.row === row && cell.col === col);
}

export function TicTacToeBoard({
  match,
  myPlayerId,
  onMove,
  disabled = false,
  lastMove,
  winningLine,
}: TicTacToeBoardProps) {
  if (!isTicTacToeState(match.state)) {
    return (
      <p className="py-ui-8 text-center text-ui-muted">
        Waiting for board state…
      </p>
    );
  }

  const effectiveLastMove =
    lastMove !== undefined ? lastMove : (match.state.lastMove ?? null);
  const effectiveWinningLine =
    winningLine !== undefined ? winningLine : (match.state.winningLine ?? null);

  const isMatchActive = match.status === 'IN_PROGRESS';
  const isMyTurn =
    !disabled && isMatchActive && match.currentTurnPlayerId === myPlayerId;

  return (
    <div role="grid" aria-label="Tic-tac-toe board">
      {match.state.board.map((rowCells, rowIndex) => (
        <div key={rowIndex} role="row">
          {rowCells.map((cellValue, colIndex) => {
            const isEmpty = cellValue === null;
            const canMove = isMyTurn && isEmpty;
            const isLast = isSameCell(effectiveLastMove, rowIndex, colIndex);
            const isWinning = Boolean(
              effectiveWinningLine?.some(
                (coord) => coord.row === rowIndex && coord.col === colIndex,
              ),
            );

            const statusLabel = isEmpty ? 'empty' : String(cellValue);
            const ariaLabel = `row ${rowIndex + 1}, column ${colIndex + 1}, ${statusLabel}`;

            return (
              <button
                key={`${rowIndex}-${colIndex}`}
                type="button"
                aria-label={ariaLabel}
                aria-disabled={!canMove}
                data-last-move={isLast ? 'true' : 'false'}
                data-winning={isWinning ? 'true' : 'false'}
                className={[
                  'ttt-cell',
                  !isMyTurn ? 'ttt-cell--disabled' : '',
                  !isEmpty ? 'ttt-cell--occupied' : '',
                  isLast ? 'ttt-cell--last-move' : '',
                  isWinning ? 'ttt-cell--winning' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={() => {
                  if (!canMove) return;
                  onMove({ row: rowIndex, col: colIndex });
                }}
              >
                {cellValue ?? ''}
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}
