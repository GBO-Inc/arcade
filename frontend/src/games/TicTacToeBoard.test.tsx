import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { MatchState } from './matchTypes';
import { TicTacToeBoard } from './TicTacToeBoard';

function createMatch(overrides: Partial<MatchState> = {}): MatchState {
  return {
    matchId: 'm1',
    roomId: 42,
    gameType: 'TIC_TAC_TOE',
    status: 'IN_PROGRESS',
    players: ['7', '8'],
    currentTurnPlayerId: '7',
    moveCount: 0,
    state: {
      board: [
        [null, null, null],
        [null, null, null],
        [null, null, null],
      ],
    },
    ...overrides,
  };
}

describe('TicTacToeBoard', () => {
  it('renders cells from match state with accessible labels', () => {
    const match = createMatch({
      state: {
        board: [
          ['X', null, null],
          [null, 'O', null],
          [null, null, null],
        ],
      },
    });

    render(<TicTacToeBoard match={match} myPlayerId="7" onMove={vi.fn()} />);

    expect(
      screen.getByRole('button', { name: 'row 1, column 1, X' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'row 2, column 2, O' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'row 2, column 3, empty' }),
    ).toBeInTheDocument();
  });

  it('calls onMove when clicking an empty cell on my turn', () => {
    const onMove = vi.fn();
    render(
      <TicTacToeBoard match={createMatch()} myPlayerId="7" onMove={onMove} />,
    );

    fireEvent.click(
      screen.getByRole('button', { name: 'row 2, column 3, empty' }),
    );

    expect(onMove).toHaveBeenCalledTimes(1);
    expect(onMove).toHaveBeenCalledWith({ row: 1, col: 2 });
  });

  it('does not call onMove when clicking during the opponent turn', () => {
    const onMove = vi.fn();
    const match = createMatch({ currentTurnPlayerId: '8' });

    render(<TicTacToeBoard match={match} myPlayerId="7" onMove={onMove} />);

    const cell = screen.getByRole('button', { name: 'row 1, column 1, empty' });
    fireEvent.click(cell);

    expect(onMove).not.toHaveBeenCalled();
    expect(cell).toHaveAttribute('aria-disabled', 'true');
  });

  it('does not call onMove when clicking an occupied cell', () => {
    const onMove = vi.fn();
    const match = createMatch({
      state: {
        board: [
          ['X', null, null],
          [null, null, null],
          [null, null, null],
        ],
      },
    });

    render(<TicTacToeBoard match={match} myPlayerId="7" onMove={onMove} />);

    fireEvent.click(screen.getByRole('button', { name: 'row 1, column 1, X' }));

    expect(onMove).not.toHaveBeenCalled();
  });

  it('highlights the last move and winning line', () => {
    const match = createMatch({
      status: 'FINISHED',
      currentTurnPlayerId: null,
      state: {
        board: [
          ['X', 'X', 'X'],
          ['O', 'O', null],
          [null, null, null],
        ],
      },
    });

    render(
      <TicTacToeBoard
        match={match}
        myPlayerId="7"
        onMove={vi.fn()}
        lastMove={{ row: 0, col: 2 }}
        winningLine={[
          { row: 0, col: 0 },
          { row: 0, col: 1 },
          { row: 0, col: 2 },
        ]}
      />,
    );

    expect(
      screen.getByRole('button', { name: 'row 1, column 3, X' }),
    ).toHaveAttribute('data-last-move', 'true');
    expect(
      screen.getByRole('button', { name: 'row 1, column 1, X' }),
    ).toHaveAttribute('data-winning', 'true');
    expect(
      screen.getByRole('button', { name: 'row 1, column 2, X' }),
    ).toHaveAttribute('data-winning', 'true');
    expect(
      screen.getByRole('button', { name: 'row 1, column 3, X' }),
    ).toHaveAttribute('data-winning', 'true');
  });
});
