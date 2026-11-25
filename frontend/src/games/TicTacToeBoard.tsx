import type { BoardProps } from './boardRegistry';

export interface CellCoord {
  row: number;
  col: number;
}

type Cell = string | null;

export interface TicTacToeState {
  board: Cell[][];
}

export interface TicTacToeBoardProps extends BoardProps {
  disabled?: boolean;
}

const LINES: CellCoord[][] = [
  ...[0, 1, 2].map((row) => [0, 1, 2].map((col) => ({ row, col }))),
  ...[0, 1, 2].map((col) => [0, 1, 2].map((row) => ({ row, col }))),
  [0, 1, 2].map((i) => ({ row: i, col: i })),
  [0, 1, 2].map((i) => ({ row: i, col: 2 - i })),
];

function isTicTacToeState(value: unknown): value is TicTacToeState {
  if (typeof value !== 'object' || value === null) return false;
  const record = value as { board?: unknown };
  return (
    Array.isArray(record.board) &&
    record.board.every((row) => Array.isArray(row))
  );
}

function isCellCoord(value: unknown): value is CellCoord {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as { row?: unknown }).row === 'number' &&
    typeof (value as { col?: unknown }).col === 'number'
  );
}

/** The server does not send the winning line, so it is read from the board. */
function winningLine(board: Cell[][]): CellCoord[] {
  return (
    LINES.find((line) => {
      const [first, ...rest] = line.map(({ row, col }) => board[row]?.[col]);
      return first != null && rest.every((mark) => mark === first);
    }) ?? []
  );
}

function cellClasses(canMove: boolean, isLast: boolean, isWinning: boolean) {
  return [
    'flex aspect-square items-center justify-center rounded-ui-lg border text-4xl font-bold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ui-primary-focus',
    isWinning
      ? 'border-ui-success-border bg-ui-success-surface text-ui-success-text'
      : 'border-ui-border bg-ui-surface-raised text-ui-text',
    canMove ? 'cursor-pointer hover:bg-ui-surface-hover' : 'cursor-not-allowed',
    isLast && !isWinning ? 'ring-2 ring-ui-primary' : '',
  ]
    .filter(Boolean)
    .join(' ');
}

export function TicTacToeBoard({
  match,
  myPlayerId,
  onMove,
  lastMove,
  disabled = false,
}: TicTacToeBoardProps) {
  if (!isTicTacToeState(match.state)) {
    return (
      <p className="py-ui-8 text-center text-ui-muted">
        Waiting for board state…
      </p>
    );
  }

  const { board } = match.state;
  const last = isCellCoord(lastMove) ? lastMove : null;
  const line = winningLine(board);
  const isMyTurn =
    !disabled &&
    match.status === 'IN_PROGRESS' &&
    match.currentTurnPlayerId === myPlayerId;

  return (
    <div
      role="grid"
      aria-label="Tic-tac-toe board"
      className="mx-auto grid w-full max-w-xs grid-cols-3 gap-ui-2"
    >
      {board.map((rowCells, row) => (
        <div key={row} role="row" className="contents">
          {rowCells.map((mark, col) => {
            const isEmpty = mark === null;
            const canMove = isMyTurn && isEmpty;
            const isLast = last?.row === row && last?.col === col;
            const isWinning = line.some(
              (cell) => cell.row === row && cell.col === col,
            );
            return (
              <button
                key={col}
                type="button"
                aria-label={`row ${row + 1}, column ${col + 1}, ${mark ?? 'empty'}`}
                aria-disabled={!canMove}
                data-last-move={isLast ? 'true' : 'false'}
                data-winning={isWinning ? 'true' : 'false'}
                className={cellClasses(canMove, isLast, isWinning)}
                onClick={() => {
                  if (canMove) {
                    onMove({ row, col });
                  }
                }}
              >
                {mark ?? ''}
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}
