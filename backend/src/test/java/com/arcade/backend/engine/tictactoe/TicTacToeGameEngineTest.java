package com.arcade.backend.engine.tictactoe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcade.backend.engine.MatchResult;
import com.arcade.backend.engine.MoveResult;
import com.arcade.backend.lobby.GameType;
import com.arcade.backend.match.GameEngineRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicTacToeGameEngineTest {

  private final TicTacToeGameEngine engine = new TicTacToeGameEngine();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void initialStateCreatesEmpty3x3BoardWithXStarting() {
    TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));

    assertThat(state.xPlayer()).isEqualTo("Alice");
    assertThat(state.oPlayer()).isEqualTo("Bob");
    assertThat(state.currentPlayer()).isEqualTo("Alice");
    assertThat(state.board()).hasSize(3);
    for (List<String> row : state.board()) {
      assertThat(row).containsExactly(null, null, null);
    }
    assertThat(engine.result(state)).isEqualTo(MatchResult.ongoing());
  }

  @Test
  void initialStateRejectsWrongNumberOfPlayers() {
    assertThatThrownBy(() -> engine.initialState(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> engine.initialState(List.of("Alice")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> engine.initialState(List.of("Alice", "Bob", "Charlie")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allEightWinningLinesAreDetectedForX() {
    int[][][] lines = {
      {{0, 0}, {0, 1}, {0, 2}},
      {{1, 0}, {1, 1}, {1, 2}},
      {{2, 0}, {2, 1}, {2, 2}},
      {{0, 0}, {1, 0}, {2, 0}},
      {{0, 1}, {1, 1}, {2, 1}},
      {{0, 2}, {1, 2}, {2, 2}},
      {{0, 0}, {1, 1}, {2, 2}},
      {{0, 2}, {1, 1}, {2, 0}}
    };

    for (int[][] line : lines) {
      TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));
      List<TicTacToeMove> oMoves = pickNonLineMoves(line, 2);

      state = applyValid(state, line[0][0], line[0][1]);
      state = applyValid(state, oMoves.get(0).row(), oMoves.get(0).col());
      state = applyValid(state, line[1][0], line[1][1]);
      state = applyValid(state, oMoves.get(1).row(), oMoves.get(1).col());
      state = applyValid(state, line[2][0], line[2][1]);

      assertThat(engine.result(state)).isEqualTo(MatchResult.win("Alice"));
    }
  }

  @Test
  void allEightWinningLinesAreDetectedForO() {
    int[][][] lines = {
      {{0, 0}, {0, 1}, {0, 2}},
      {{1, 0}, {1, 1}, {1, 2}},
      {{2, 0}, {2, 1}, {2, 2}},
      {{0, 0}, {1, 0}, {2, 0}},
      {{0, 1}, {1, 1}, {2, 1}},
      {{0, 2}, {1, 2}, {2, 2}},
      {{0, 0}, {1, 1}, {2, 2}},
      {{0, 2}, {1, 1}, {2, 0}}
    };

    for (int[][] line : lines) {
      TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));
      List<TicTacToeMove> xMoves = pickNonLineMoves(line, 3);

      state = applyValid(state, xMoves.get(0).row(), xMoves.get(0).col());
      state = applyValid(state, line[0][0], line[0][1]);
      state = applyValid(state, xMoves.get(1).row(), xMoves.get(1).col());
      state = applyValid(state, line[1][0], line[1][1]);
      state = applyValid(state, xMoves.get(2).row(), xMoves.get(2).col());
      state = applyValid(state, line[2][0], line[2][1]);

      assertThat(engine.result(state)).isEqualTo(MatchResult.win("Bob"));
    }
  }

  @Test
  void rejectsMoveOntoOccupiedCellWithReason() {
    TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));
    state = applyValid(state, 1, 1);

    MoveResult<TicTacToeState> occupied = engine.applyMove(state, new TicTacToeMove(1, 1));
    assertThat(occupied.successful()).isFalse();
    assertThat(occupied.rejectionReason()).isNotBlank();
  }

  @Test
  void rejectsMoveOutsideTheBoard() {
    TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));

    for (TicTacToeMove invalid :
        List.of(
            new TicTacToeMove(-1, 0),
            new TicTacToeMove(0, -1),
            new TicTacToeMove(3, 0),
            new TicTacToeMove(0, 3),
            new TicTacToeMove(5, 5))) {
      MoveResult<TicTacToeState> res = engine.applyMove(state, invalid);
      assertThat(res.successful()).isFalse();
      assertThat(res.rejectionReason()).isNotBlank();
    }
  }

  @Test
  void fullBoardWithoutWinningLineIsADraw() {
    // X O X
    // X O O
    // O X X
    TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));
    int[][] moves = {{0, 0}, {0, 1}, {0, 2}, {1, 1}, {1, 0}, {1, 2}, {2, 1}, {2, 0}, {2, 2}};
    for (int[] m : moves) {
      state = applyValid(state, m[0], m[1]);
    }

    assertThat(engine.result(state)).isEqualTo(MatchResult.draw());
    assertThat(engine.legalMoves(state, "Alice")).isEmpty();
    assertThat(engine.legalMoves(state, "Bob")).isEmpty();

    MoveResult<TicTacToeState> afterDraw = engine.applyMove(state, new TicTacToeMove(0, 0));
    assertThat(afterDraw.successful()).isFalse();
    assertThat(afterDraw.rejectionReason()).isNotBlank();
  }

  @Test
  void winOnTheLastCellIsAWinNotADraw() {
    // X O X
    // O O X
    // O X X  <- X wins on the 9th move at (2, 2)
    TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));
    int[][] moves = {{0, 0}, {0, 1}, {0, 2}, {1, 0}, {1, 2}, {1, 1}, {2, 1}, {2, 0}, {2, 2}};
    for (int[] m : moves) {
      state = applyValid(state, m[0], m[1]);
    }

    assertThat(engine.result(state)).isEqualTo(MatchResult.win("Alice"));
  }

  @Test
  void noMoveIsAcceptedAfterGameIsWon() {
    TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));
    state = applyValid(state, 0, 0);
    state = applyValid(state, 1, 0);
    state = applyValid(state, 0, 1);
    state = applyValid(state, 1, 1);
    state = applyValid(state, 0, 2);

    assertThat(engine.result(state)).isEqualTo(MatchResult.win("Alice"));
    MoveResult<TicTacToeState> afterWin = engine.applyMove(state, new TicTacToeMove(2, 2));
    assertThat(afterWin.successful()).isFalse();
    assertThat(afterWin.rejectionReason()).isNotBlank();
  }

  @Test
  void legalMovesReturnsExactlyTheEmptyCellsForCurrentPlayer() {
    TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));

    assertThat(engine.legalMoves(state, "Alice")).hasSize(9);
    assertThat(engine.legalMoves(state, "Bob")).isEmpty();

    state = applyValid(state, 0, 0);
    state = applyValid(state, 1, 1);

    List<TicTacToeMove> legal = engine.legalMoves(state, "Alice");
    assertThat(legal).hasSize(7).doesNotContain(new TicTacToeMove(0, 0), new TicTacToeMove(1, 1));
    assertThat(engine.legalMoves(state, "Bob")).isEmpty();
  }

  @Test
  void stateAndMoveAreSerializableToJson() throws Exception {
    TicTacToeState state = engine.initialState(List.of("Alice", "Bob"));
    state = applyValid(state, 0, 0);
    TicTacToeMove move = new TicTacToeMove(1, 2);

    String stateJson = objectMapper.writeValueAsString(state);
    String moveJson = objectMapper.writeValueAsString(move);

    assertThat(objectMapper.readValue(stateJson, TicTacToeState.class)).isEqualTo(state);
    assertThat(objectMapper.readValue(moveJson, TicTacToeMove.class)).isEqualTo(move);
  }

  @Test
  void engineRegistersUnderTicTacToeId() {
    GameEngineRegistry registry = new GameEngineRegistry();
    assertThat(registry.getById("tic-tac-toe")).isInstanceOf(TicTacToeGameEngine.class);
    assertThat(registry.get(GameType.TIC_TAC_TOE)).isInstanceOf(TicTacToeGameEngine.class);
  }

  private TicTacToeState applyValid(TicTacToeState state, int row, int col) {
    MoveResult<TicTacToeState> res = engine.applyMove(state, new TicTacToeMove(row, col));
    assertThat(res.successful()).isTrue();
    return res.newState();
  }

  private List<TicTacToeMove> pickNonLineMoves(int[][] line, int count) {
    List<TicTacToeMove> candidates = new ArrayList<>();
    for (int r = 0; r < 3; r++) {
      for (int c = 0; c < 3; c++) {
        boolean inLine = false;
        for (int[] cell : line) {
          if (cell[0] == r && cell[1] == c) {
            inLine = true;
            break;
          }
        }
        if (!inLine) {
          candidates.add(new TicTacToeMove(r, c));
        }
      }
    }
    // Avoid accidentally forming a winning line with the 3 non-line moves when testing O wins
    for (int i = 0; i < candidates.size(); i++) {
      for (int j = i + 1; j < candidates.size(); j++) {
        if (count == 2) {
          return List.of(candidates.get(i), candidates.get(j));
        }
        for (int k = j + 1; k < candidates.size(); k++) {
          List<TicTacToeMove> triple =
              List.of(candidates.get(i), candidates.get(j), candidates.get(k));
          if (!isWinningTriple(triple)) {
            return triple;
          }
        }
      }
    }
    throw new IllegalStateException("Could not find non-winning moves");
  }

  private boolean isWinningTriple(List<TicTacToeMove> triple) {
    int r0 = triple.get(0).row(), c0 = triple.get(0).col();
    int r1 = triple.get(1).row(), c1 = triple.get(1).col();
    int r2 = triple.get(2).row(), c2 = triple.get(2).col();
    if (r0 == r1 && r1 == r2) return true;
    if (c0 == c1 && c1 == c2) return true;
    if (r0 == c0 && r1 == c1 && r2 == c2) return true;
    return (r0 + c0 == 2) && (r1 + c1 == 2) && (r2 + c2 == 2);
  }
}
