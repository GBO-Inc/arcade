package com.arcade.backend.match;

import java.util.UUID;

public interface MatchResultStore {
  void save(MatchFinalResult result);

  boolean existsByMatchId(UUID matchId);
}
