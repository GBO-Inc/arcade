package com.arcade.backend.match;

import com.arcade.backend.engine.GameEngine;
import com.arcade.backend.engine.MoveResult;
import com.arcade.backend.lobby.GameType;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MatchService {

  private final MatchRepository matches;
  private final GameEngineRegistry engineRegistry;
  private final RandomGenerator random;

  @Autowired
  public MatchService(MatchRepository matches, GameEngineRegistry engineRegistry) {
    this(matches, engineRegistry, new SecureRandom());
  }

  MatchService(MatchRepository matches, GameEngineRegistry engineRegistry, RandomGenerator random) {
    this.matches = matches;
    this.engineRegistry = engineRegistry;
    this.random = random;
  }

  public <TState, TMove> Match<TState, TMove> createFromFullRoom(
      long roomId, GameType gameType, List<String> playerIds) {
    if (playerIds == null || playerIds.size() != 2) {
      throw new IllegalArgumentException("A match requires exactly 2 players");
    }

    List<String> orderedPlayers =
        random.nextBoolean()
            ? List.of(playerIds.get(0), playerIds.get(1))
            : List.of(playerIds.get(1), playerIds.get(0));

    GameEngine<TState, TMove, String> engine = engineRegistry.get(gameType);
    TState initialState = engine.initialState(orderedPlayers);

    Match<TState, TMove> match =
        new Match<>(UUID.randomUUID(), roomId, gameType, orderedPlayers, initialState);
    matches.save(match);
    return match;
  }

  public MatchStateResponse connectPlayer(UUID matchId, String playerId) {
    Match<?, ?> match = getMatchOrThrow(matchId);
    if (!match.getPlayers().contains(playerId)) {
      throw MatchException.notParticipant();
    }
    match.markPlayerConnected(playerId);
    matches.save(match);
    return MatchStateResponse.from(match);
  }

  @SuppressWarnings("unchecked")
  public <TState, TMove> MatchStateResponse submitMove(UUID matchId, String playerId, TMove move) {
    Match<TState, TMove> match = (Match<TState, TMove>) getMatchOrThrow(matchId);

    if (!match.getPlayers().contains(playerId)) {
      throw MatchException.notParticipant();
    }
    if (match.getStatus() != MatchStatus.IN_PROGRESS) {
      throw MatchException.notInProgress();
    }
    if (!match.getCurrentTurnPlayerId().equals(playerId)) {
      throw MatchException.notYourTurn();
    }

    GameEngine<TState, TMove, String> engine = engineRegistry.get(match.getGameType());
    MoveResult<TState> result = engine.applyMove(match.getState(), move);
    if (!result.successful()) {
      throw MatchException.invalidMove(result.rejectionReason());
    }

    match.recordMove(move, result.newState());
    matches.save(match);
    return MatchStateResponse.from(match);
  }

  public MatchStateResponse getState(UUID matchId, String playerId) {
    Match<?, ?> match = getMatchOrThrow(matchId);
    if (!match.getPlayers().contains(playerId)) {
      throw MatchException.notParticipant();
    }
    return MatchStateResponse.from(match);
  }

  private Match<?, ?> getMatchOrThrow(UUID matchId) {
    return matches.findById(matchId).orElseThrow(MatchException::notFound);
  }
}
