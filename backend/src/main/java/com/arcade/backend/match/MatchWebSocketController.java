package com.arcade.backend.match;

import com.arcade.backend.engine.dummy.DummyMove;
import com.arcade.backend.engine.tictactoe.TicTacToeMove;
import com.arcade.backend.lobby.GameType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class MatchWebSocketController {

  private final MatchService matchService;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;

  public MatchWebSocketController(
      MatchService matchService,
      ObjectMapper objectMapper,
      SimpMessagingTemplate messagingTemplate) {
    this.matchService = matchService;
    this.objectMapper = objectMapper;
    this.messagingTemplate = messagingTemplate;
  }

  @MessageMapping("/match/{id}/move")
  public void handleMove(
      @DestinationVariable("id") UUID matchId, @Payload JsonNode payload, Principal principal) {
    GameType gameType = matchService.getMatchGameType(matchId);
    Object move = deserializeMove(gameType, payload);
    matchService.submitMove(matchId, principal.getName(), move);
  }

  @MessageExceptionHandler(MatchException.class)
  public void handleMatchException(
      MatchException ex, @DestinationVariable("id") UUID matchId, Principal principal) {
    if (principal == null) {
      return;
    }
    MatchErrorMessage error =
        new MatchErrorMessage(ex.getCode(), ex.getBody().getDetail(), matchId);
    messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", error);
  }

  private Object deserializeMove(GameType gameType, JsonNode payload) {
    if (payload == null || payload.isNull()) {
      throw MatchException.invalidMove("Move payload is required");
    }
    try {
      return switch (gameType) {
        case TIC_TAC_TOE -> objectMapper.treeToValue(payload, TicTacToeMove.class);
        case CONNECT_FOUR, CHECKERS -> objectMapper.treeToValue(payload, DummyMove.class);
      };
    } catch (JsonProcessingException | IllegalArgumentException ex) {
      throw MatchException.invalidMove("Malformed move payload");
    }
  }
}
