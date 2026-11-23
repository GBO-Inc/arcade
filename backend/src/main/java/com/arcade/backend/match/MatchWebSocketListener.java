package com.arcade.backend.match;

import java.security.Principal;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
public class MatchWebSocketListener {

  private static final String MATCH_TOPIC_PREFIX = "/topic/matches/";

  private final MatchService matchService;
  private final SimpMessagingTemplate messagingTemplate;

  public MatchWebSocketListener(
      MatchService matchService, SimpMessagingTemplate messagingTemplate) {
    this.matchService = matchService;
    this.messagingTemplate = messagingTemplate;
  }

  @EventListener
  public void handleSubscribe(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String destination = accessor.getDestination();
    Principal user = accessor.getUser();
    if (destination == null || user == null || !destination.startsWith(MATCH_TOPIC_PREFIX)) {
      return;
    }

    String rawId = destination.substring(MATCH_TOPIC_PREFIX.length());
    UUID matchId;
    try {
      matchId = UUID.fromString(rawId);
    } catch (IllegalArgumentException ex) {
      return;
    }

    MatchStateResponse state = matchService.connectPlayer(matchId, user.getName());
    messagingTemplate.convertAndSend(destination, state);
  }
}
