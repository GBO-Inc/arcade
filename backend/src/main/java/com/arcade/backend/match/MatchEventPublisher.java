package com.arcade.backend.match;

public interface MatchEventPublisher {
  void publishMoveApplied(MatchMoveEvent event);

  void publishMatchEnded(MatchFinalResult result);
}
