package com.arcade.backend.match;

import com.arcade.backend.engine.GameEngine;
import com.arcade.backend.engine.dummy.DummyGameEngine;
import com.arcade.backend.lobby.GameType;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GameEngineRegistry {

  private final Map<GameType, GameEngine<?, ?, String>> engines = new EnumMap<>(GameType.class);

  public GameEngineRegistry() {
    DummyGameEngine dummy = new DummyGameEngine();
    for (GameType type : GameType.values()) {
      engines.put(type, dummy);
    }
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
}
