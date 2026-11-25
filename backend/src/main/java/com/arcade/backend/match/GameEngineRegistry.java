package com.arcade.backend.match;

import com.arcade.backend.engine.GameEngine;
import com.arcade.backend.engine.dummy.DummyGameEngine;
import com.arcade.backend.engine.tictactoe.TicTacToeGameEngine;
import com.arcade.backend.lobby.GameType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GameEngineRegistry {

  private final Map<GameType, GameEngine<?, ?, String>> engines = new EnumMap<>(GameType.class);
  private final Map<String, GameEngine<?, ?, String>> enginesById = new HashMap<>();

  public GameEngineRegistry() {
    DummyGameEngine dummy = new DummyGameEngine();
    for (GameType type : GameType.values()) {
      engines.put(type, dummy);
    }
    TicTacToeGameEngine ticTacToe = new TicTacToeGameEngine();
    engines.put(GameType.TIC_TAC_TOE, ticTacToe);
    enginesById.put(ticTacToe.gameId(), ticTacToe);
  }

  public GameEngineRegistry(Map<GameType, GameEngine<?, ?, String>> customEngines) {
    engines.putAll(customEngines);
  }

  @SuppressWarnings("unchecked")
  public <TState, TMove> GameEngine<TState, TMove, String> get(GameType gameType) {
    GameEngine<?, ?, String> engine = engines.get(gameType);
    if (engine == null) {
      throw new IllegalArgumentException("No engine registered for " + gameType);
    }
    return (GameEngine<TState, TMove, String>) engine;
  }

  @SuppressWarnings("unchecked")
  public <TState, TMove> GameEngine<TState, TMove, String> getById(String gameId) {
    GameEngine<?, ?, String> engine = enginesById.get(gameId);
    if (engine == null) {
      throw new IllegalArgumentException("No engine registered for id: " + gameId);
    }
    return (GameEngine<TState, TMove, String>) engine;
  }
}
