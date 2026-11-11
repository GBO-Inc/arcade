package com.arcade.backend.engine.dummy;

import com.arcade.backend.engine.GameEngine;
import com.arcade.backend.engine.MatchResult;
import com.arcade.backend.engine.MoveResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DummyGameEngine implements GameEngine<DummyState, DummyMove, String> {

  private static final int WINNING_SCORE = 3;

  @Override
  public DummyState initialState(List<String> players) {
    if (players == null || players.size() != 2) {
      throw new IllegalArgumentException("Exactly 2 players are required");
    }
    Map<String, Integer> initialScores =
        Map.of(
            players.get(0), 0,
            players.get(1), 0);
    return new DummyState(initialScores, players.get(0), players.get(1));
  }

  @Override
  public List<DummyMove> legalMoves(DummyState state, String player) {
    if (result(state).status() != MatchResult.Status.ONGOING) {
      return List.of();
    }
    if (!state.currentPlayer().equals(player)) {
      return List.of();
    }
    return List.of(new DummyMove(player));
  }

  @Override
  public MoveResult<DummyState> applyMove(DummyState state, DummyMove move) {
    if (result(state).status() != MatchResult.Status.ONGOING) {
      return MoveResult.rejected("Match is already finished");
    }
    if (!state.currentPlayer().equals(move.playerId())) {
      return MoveResult.rejected("Not your turn");
    }

    Map<String, Integer> newScores = new HashMap<>(state.scores());
    newScores.put(move.playerId(), newScores.get(move.playerId()) + 1);

    DummyState newState = new DummyState(newScores, state.waitingPlayer(), state.currentPlayer());
    return MoveResult.success(newState);
  }

  @Override
  public MatchResult<String> result(DummyState state) {
    for (Map.Entry<String, Integer> entry : state.scores().entrySet()) {
      if (entry.getValue() >= WINNING_SCORE) {
        return MatchResult.win(entry.getKey());
      }
    }

    return MatchResult.ongoing();
  }
}
