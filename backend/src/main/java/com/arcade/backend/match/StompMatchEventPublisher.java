package com.arcade.backend.match;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompMatchEventPublisher implements MatchEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public StompMatchEventPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void publishMoveApplied(MatchMoveEvent event) {
    messagingTemplate.convertAndSend("/topic/matches/" + event.match().matchId(), event);
  }

  @Override
  public void publishMatchEnded(MatchFinalResult result) {
    for (String playerId : result.players()) {
      messagingTemplate.convertAndSendToUser(playerId, "/queue/match-result", result);
    }
    messagingTemplate.convertAndSend("/topic/matches/" + result.matchId(), result);
  }
}
