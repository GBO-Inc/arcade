package com.arcade.backend.lobby;

import java.util.UUID;

public record MatchStartedEvent(String type, UUID matchId) {

  public MatchStartedEvent(UUID matchId) {
    this("MATCH_STARTED", matchId);
  }
}
