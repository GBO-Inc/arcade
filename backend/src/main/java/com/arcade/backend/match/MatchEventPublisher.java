package com.arcade.backend.match;

public interface MatchEventPublisher {
  void publishMatchEnded(MatchFinalResult result);
}
