package com.arcade.backend.engine;

import java.util.List;

public interface GameEngine<TState, TMove, TPlayer> {
    
    TState initialState(List<TPlayer> players);
    
    List<TMove> legalMoves(TState state, TPlayer player);
    
    // Visszatérhet az új állapottal, vagy egy elutasítási okkal (pl. Either/Result pattern)
    MoveResult<TState> applyMove(TState state, TMove move);
    
    MatchResult result(TState state);
}