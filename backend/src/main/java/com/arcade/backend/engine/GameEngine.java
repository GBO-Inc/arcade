package com.arcade.backend.engine;

import java.util.List;

public interface GameEngine<TState, TMove, TPlayer> {
  TState initialState(List<TPlayer> players);

  List<TMove> legalMoves(TState state, TPlayer player);

  MoveResult<TState> applyMove(TState state, TMove move);

  MatchResult<TPlayer> result(TState state);
}
