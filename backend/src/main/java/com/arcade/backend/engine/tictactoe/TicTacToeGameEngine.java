package com.arcade.backend.engine.tictactoe;

import com.arcade.backend.engine.GameEngine;
import com.arcade.backend.engine.MatchResult;
import com.arcade.backend.engine.MoveResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TicTacToeGameEngine implements GameEngine<TicTacToeState, TicTacToeMove, String> {

  public static final String GAME_ID = "tic-tac-toe";
  private static final int BOARD_SIZE = 3;
  private static final String MARK_X = "X";
  private static final String MARK_O = "O";

  private static final int[][][] WINNING_LINES = {
    // Rows
    {{0, 0}, {0, 1}, {0, 2}},
    {{1, 0}, {1, 1}, {1, 2}},
    {{2, 0}, {2, 1}, {2, 2}},
    // Columns
    {{0, 0}, {1, 0}, {2, 0}},
    {{0, 1}, {1, 1}, {2, 1}},
    {{0, 2}, {1, 2}, {2, 2}},
    // Diagonals
    {{0, 0}, {1, 1}, {2, 2}},
    {{0, 2}, {1, 1}, {2, 0}}
  };

  public String gameId() {
    return GAME_ID;
  }

  @Override
  public TicTacToeState initialState(List<String> players) {
    if (players == null || players.size() != 2) {
      throw new IllegalArgumentException("Exactly 2 players are required");
    }
    List<List<String>> emptyBoard = new ArrayList<>(BOARD_SIZE);
    for (int r = 0; r < BOARD_SIZE; r++) {
      List<String> row = new ArrayList<>(BOARD_SIZE);
      for (int c = 0; c < BOARD_SIZE; c++) {
        row.add(null);
      }
      emptyBoard.add(Collections.unmodifiableList(row));
    }
    return new TicTacToeState(
        Collections.unmodifiableList(emptyBoard), players.get(0), players.get(1), players.get(0));
  }

  @Override
  public List<TicTacToeMove> legalMoves(TicTacToeState state, String player) {
    if (result(state).status() != MatchResult.Status.ONGOING) {
      return List.of();
    }
    if (!state.currentPlayer().equals(player)) {
      return List.of();
    }
    List<TicTacToeMove> moves = new ArrayList<>();
    for (int r = 0; r < BOARD_SIZE; r++) {
      for (int c = 0; c < BOARD_SIZE; c++) {
        if (state.board().get(r).get(c) == null) {
          moves.add(new TicTacToeMove(r, c));
        }
      }
    }
    return List.copyOf(moves);
  }

  @Override
  public MoveResult<TicTacToeState> applyMove(TicTacToeState state, TicTacToeMove move) {
    if (result(state).status() != MatchResult.Status.ONGOING) {
      return MoveResult.rejected("Match is already finished");
    }
    if (move == null) {
      return MoveResult.rejected("Move must not be null");
    }
    if (move.row() < 0 || move.row() >= BOARD_SIZE || move.col() < 0 || move.col() >= BOARD_SIZE) {
      return MoveResult.rejected("Move is outside the board");
    }
    if (state.board().get(move.row()).get(move.col()) != null) {
      return MoveResult.rejected("Cell is already occupied");
    }

    String mark = state.currentPlayer().equals(state.xPlayer()) ? MARK_X : MARK_O;
    String nextPlayer =
        state.currentPlayer().equals(state.xPlayer()) ? state.oPlayer() : state.xPlayer();

    List<List<String>> newBoard = new ArrayList<>(BOARD_SIZE);
    for (int r = 0; r < BOARD_SIZE; r++) {
      List<String> newRow = new ArrayList<>(state.board().get(r));
      if (r == move.row()) {
        newRow.set(move.col(), mark);
      }
      newBoard.add(Collections.unmodifiableList(newRow));
    }

    TicTacToeState newState =
        new TicTacToeState(
            Collections.unmodifiableList(newBoard), state.xPlayer(), state.oPlayer(), nextPlayer);
    return MoveResult.success(newState);
  }

  @Override
  public MatchResult<String> result(TicTacToeState state) {
    List<List<String>> board = state.board();

    for (int[][] line : WINNING_LINES) {
      String first = board.get(line[0][0]).get(line[0][1]);
      if (first != null
          && first.equals(board.get(line[1][0]).get(line[1][1]))
          && first.equals(board.get(line[2][0]).get(line[2][1]))) {
        String winner = first.equals(MARK_X) ? state.xPlayer() : state.oPlayer();
        return MatchResult.win(winner);
      }
    }

    for (int r = 0; r < BOARD_SIZE; r++) {
      for (int c = 0; c < BOARD_SIZE; c++) {
        if (board.get(r).get(c) == null) {
          return MatchResult.ongoing();
        }
      }
    }

    return MatchResult.draw();
  }
}
