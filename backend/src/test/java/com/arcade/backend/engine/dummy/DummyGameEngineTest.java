package com.arcade.backend.engine.dummy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcade.backend.engine.MatchResult;
import com.arcade.backend.engine.MoveResult;
import com.arcade.backend.match.MatchStateMachine;
import com.arcade.backend.match.MatchStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DummyGameEngineTest {

  private final DummyGameEngine engine = new DummyGameEngine();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void dummyGameDrivesMatchStateMachineFromWaitingToFinished() {
    MatchStateMachine stateMachine = new MatchStateMachine();
    assertThat(stateMachine.getCurrentState()).isEqualTo(MatchStatus.WAITING);

    DummyState state = engine.initialState(List.of("Alice", "Bob"));
    stateMachine.transitionTo(MatchStatus.IN_PROGRESS);
    assertThat(stateMachine.getCurrentState()).isEqualTo(MatchStatus.IN_PROGRESS);

    List<DummyMove> moves =
        List.of(
            new DummyMove("Alice"),
            new DummyMove("Bob"),
            new DummyMove("Alice"),
            new DummyMove("Bob"),
            new DummyMove("Alice"));

    for (DummyMove move : moves) {
      assertThat(stateMachine.getCurrentState()).isEqualTo(MatchStatus.IN_PROGRESS);
      MoveResult<DummyState> moveResult = engine.applyMove(state, move);
      assertThat(moveResult.successful()).isTrue();
      state = moveResult.newState();

      if (engine.result(state).status() != MatchResult.Status.ONGOING) {
        stateMachine.transitionTo(MatchStatus.FINISHED);
      }
    }

    assertThat(stateMachine.getCurrentState()).isEqualTo(MatchStatus.FINISHED);
    MatchResult<String> result = engine.result(state);
    assertThat(result.status()).isEqualTo(MatchResult.Status.WIN);
    assertThat(result.winner()).isEqualTo("Alice");
  }

  @Test
  void initialStateRejectsWrongNumberOfPlayers() {
    assertThatThrownBy(() -> engine.initialState(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Exactly 2 players");

    assertThatThrownBy(() -> engine.initialState(List.of("Alice")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Exactly 2 players");

    assertThatThrownBy(() -> engine.initialState(List.of("Alice", "Bob", "Charlie")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Exactly 2 players");
  }

  @Test
  void legalMovesReturnsMoveForCurrentPlayerAndEmptyForWaitingPlayerOrFinishedMatch() {
    DummyState state = engine.initialState(List.of("Alice", "Bob"));

    assertThat(engine.legalMoves(state, "Alice")).containsExactly(new DummyMove("Alice"));
    assertThat(engine.legalMoves(state, "Bob")).isEmpty();

    state = engine.applyMove(state, new DummyMove("Alice")).newState();
    state = engine.applyMove(state, new DummyMove("Bob")).newState();
    state = engine.applyMove(state, new DummyMove("Alice")).newState();
    state = engine.applyMove(state, new DummyMove("Bob")).newState();
    state = engine.applyMove(state, new DummyMove("Alice")).newState();

    assertThat(engine.legalMoves(state, "Alice")).isEmpty();
    assertThat(engine.legalMoves(state, "Bob")).isEmpty();
  }

  @Test
  void applyMoveRejectsWrongTurnAndMoveAfterMatchIsWon() {
    DummyState state = engine.initialState(List.of("Alice", "Bob"));

    MoveResult<DummyState> wrongTurn = engine.applyMove(state, new DummyMove("Bob"));
    assertThat(wrongTurn.successful()).isFalse();
    assertThat(wrongTurn.rejectionReason()).isEqualTo("Not your turn");

    state = engine.applyMove(state, new DummyMove("Alice")).newState();
    state = engine.applyMove(state, new DummyMove("Bob")).newState();
    state = engine.applyMove(state, new DummyMove("Alice")).newState();
    state = engine.applyMove(state, new DummyMove("Bob")).newState();
    state = engine.applyMove(state, new DummyMove("Alice")).newState();

    MoveResult<DummyState> afterWin = engine.applyMove(state, new DummyMove("Bob"));
    assertThat(afterWin.successful()).isFalse();
    assertThat(afterWin.rejectionReason()).isEqualTo("Match is already finished");
  }

  @Test
  void stateAndMoveAreSerializableToJson() throws Exception {
    DummyState originalState = engine.initialState(List.of("Alice", "Bob"));
    DummyMove originalMove = new DummyMove("Alice");

    String stateJson = objectMapper.writeValueAsString(originalState);
    String moveJson = objectMapper.writeValueAsString(originalMove);

    DummyState deserializedState = objectMapper.readValue(stateJson, DummyState.class);
    DummyMove deserializedMove = objectMapper.readValue(moveJson, DummyMove.class);

    assertThat(deserializedState).isEqualTo(originalState);
    assertThat(deserializedMove).isEqualTo(originalMove);
  }
}
