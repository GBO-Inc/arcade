package com.arcade.backend.match;

import com.arcade.backend.lobby.GameType;
import java.util.List;
import java.util.UUID;

public record MatchFinalResult(
    UUID matchId,
    long roomId,
    GameType gameType,
    List<String> players,
    Outcome outcome,
    String winnerId,
    String reason) {

  public enum Outcome {
    WIN,
    DRAW,
    ABANDONED
  }
}
