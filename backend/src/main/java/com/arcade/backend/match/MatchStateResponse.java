package com.arcade.backend.match;

import com.arcade.backend.lobby.GameType;
import java.util.List;
import java.util.UUID;

public record MatchStateResponse(
    UUID matchId,
    long roomId,
    GameType gameType,
    MatchStatus status,
    List<String> players,
    String currentTurnPlayerId,
    Object state,
    int moveCount) {

  public static MatchStateResponse from(Match<?, ?> match) {
    return new MatchStateResponse(
        match.getId(),
        match.getRoomId(),
        match.getGameType(),
        match.getStatus(),
        match.getPlayers(),
        match.getCurrentTurnPlayerId(),
        match.getState(),
        match.getMoves().size());
  }
}
