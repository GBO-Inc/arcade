package com.arcade.backend.engine.dummy;

import static org.junit.jupiter.api.Assertions.*;

import com.arcade.backend.engine.MatchResult;
import com.arcade.backend.engine.MoveResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DummyGameEngineTest {

  private DummyGameEngine engine;
  private final String p1 = "Player1";
  private final String p2 = "Player2";

  @BeforeEach
  void setUp() {
    engine = new DummyGameEngine();
  }

  @Test
  void testGameFlowToWin() {
    DummyState state = engine.initialState(List.of(p1, p2));
    assertEquals(MatchResult.ONGOING, engine.result(state));

    // Player 1 kapja az első 2 pontot
    state = engine.applyMove(state, new DummyMove(p1)).newState(); // P1: 1
    state = engine.applyMove(state, new DummyMove(p2)).newState(); // P2: 1
    state = engine.applyMove(state, new DummyMove(p1)).newState(); // P1: 2
    state = engine.applyMove(state, new DummyMove(p2)).newState(); // P2: 2

    assertEquals(MatchResult.ONGOING, engine.result(state));

    // Player 1 eléri a 3. pontot
    MoveResult<DummyState> finalMove = engine.applyMove(state, new DummyMove(p1));
    assertTrue(finalMove.successful());

    assertEquals(MatchResult.WIN, engine.result(finalMove.newState()));
  }

  @Test
  void testInvalidMoveRejected() {
    DummyState state = engine.initialState(List.of(p1, p2));

    // P2 próbál lépni, amikor P1 jön
    MoveResult<DummyState> result = engine.applyMove(state, new DummyMove(p2));

    assertFalse(result.successful());
    assertEquals("Not your turn", result.rejectionReason());
  }
}
