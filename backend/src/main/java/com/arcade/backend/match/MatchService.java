package com.arcade.backend.match;

import com.arcade.backend.engine.GameEngine;
import com.arcade.backend.engine.MatchResult;
import com.arcade.backend.engine.MoveResult;
import com.arcade.backend.lobby.GameType;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MatchService implements DisposableBean {

  private final MatchRepository matches;
  private final GameEngineRegistry engineRegistry;
  private final MatchResultStore resultStore;
  private final MatchEventPublisher eventPublisher;
  private final Duration disconnectTimeout;
  private final RandomGenerator random;
  private final ScheduledExecutorService scheduler;

  @Autowired
  public MatchService(
      MatchRepository matches,
      GameEngineRegistry engineRegistry,
      MatchResultStore resultStore,
      MatchEventPublisher eventPublisher,
      @Value("${arcade.match.disconnect-timeout:30s}") Duration disconnectTimeout) {
    this(
        matches,
        engineRegistry,
        resultStore,
        eventPublisher,
        disconnectTimeout,
        new SecureRandom());
  }

  MatchService(
      MatchRepository matches,
      GameEngineRegistry engineRegistry,
      MatchResultStore resultStore,
      MatchEventPublisher eventPublisher,
      Duration disconnectTimeout,
      RandomGenerator random) {
    this.matches = matches;
    this.engineRegistry = engineRegistry;
    this.resultStore = resultStore;
    this.eventPublisher = eventPublisher;
    this.disconnectTimeout = disconnectTimeout;
    this.random = random;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
          Thread t = new Thread(r, "match-disconnect-timer");
          t.setDaemon(true);
          return t;
        });
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

  /** A player left the full room before the match started, so this match can never begin. */
  public void abandonWaitingMatch(long roomId) {
    matches
        .findByRoomId(roomId)
        .filter(match -> match.getStatus() == MatchStatus.WAITING)
        .ifPresent(match -> {
          match.getLock().lock();
          try {
            if (match.getStatus() == MatchStatus.WAITING) {
              match.abandon();
              // It never started, so there is no result to save; just free the memory.
              matches.deleteById(match.getId());
            }
          } finally {
            match.getLock().unlock();
          }
        });
  }

  public MatchStateResponse connectPlayer(UUID matchId, String playerId) {
    Match<?, ?> match = getMatchOrThrow(matchId);
    match.getLock().lock();
    try {
      if (!match.getPlayers().contains(playerId)) {
        throw MatchException.notParticipant();
      }
      if (match.isTerminal()) {
        throw MatchException.notInProgress();
      }
      match.markPlayerConnected(playerId);
      matches.save(match);
      return MatchStateResponse.from(match);
    } finally {
      match.getLock().unlock();
    }
  }

  public void disconnectPlayer(UUID matchId, String playerId) {
    matches
        .findById(matchId)
        .ifPresent(match -> {
          match.getLock().lock();
          try {
            boolean allDisconnected = match.markPlayerDisconnected(playerId);
            if (allDisconnected) {
              scheduleAbandonOnTimeout(match);
            }
          } finally {
            match.getLock().unlock();
          }
        });
  }

  public void handlePlayerSessionClosed(String playerId) {
    matches.findActiveByPlayerId(playerId).ifPresent(m -> disconnectPlayer(m.getId(), playerId));
  }

  public GameType getMatchGameType(UUID matchId) {
    return getLiveMatchOrThrow(matchId).getGameType();
  }

  @SuppressWarnings("unchecked")
  public <TState, TMove> MatchStateResponse submitMove(UUID matchId, String playerId, TMove move) {
    Match<TState, TMove> match = (Match<TState, TMove>) getLiveMatchOrThrow(matchId);

    match.getLock().lock();
    try {
      if (!match.getPlayers().contains(playerId)) {
        throw MatchException.notParticipant();
      }
      if (match.isTerminal()) {
        throw MatchException.matchFinished();
      }
      if (match.getStatus() != MatchStatus.IN_PROGRESS) {
        throw MatchException.notInProgress();
      }
      if (!match.getCurrentTurnPlayerId().equals(playerId)) {
        throw MatchException.notYourTurn();
      }

      GameEngine<TState, TMove, String> engine = engineRegistry.get(match.getGameType());
      MoveResult<TState> moveResult = engine.applyMove(match.getState(), move);
      if (!moveResult.successful()) {
        throw MatchException.invalidMove(moveResult.rejectionReason());
      }

      match.recordMove(move, moveResult.newState());

      MatchResult<String> outcome = engine.result(moveResult.newState());
      if (outcome.status() != MatchResult.Status.ONGOING) {
        match.finish();
        MatchStateResponse finalSnapshot = MatchStateResponse.from(match);
        eventPublisher.publishMoveApplied(new MatchMoveEvent(finalSnapshot, move, playerId));
        finalizeMatch(match, toFinalResult(match, outcome));
        return finalSnapshot;
      }

      matches.save(match);
      MatchStateResponse snapshot = MatchStateResponse.from(match);
      eventPublisher.publishMoveApplied(new MatchMoveEvent(snapshot, move, playerId));
      return snapshot;
    } finally {
      match.getLock().unlock();
    }
  }

  public MatchStateResponse getState(UUID matchId, String playerId) {
    Match<?, ?> match = getMatchOrThrow(matchId);
    match.getLock().lock();
    try {
      if (!match.getPlayers().contains(playerId)) {
        throw MatchException.notParticipant();
      }
      return MatchStateResponse.from(match);
    } finally {
      match.getLock().unlock();
    }
  }

  private void scheduleAbandonOnTimeout(Match<?, ?> match) {
    long delayMs = Math.max(0L, disconnectTimeout.toMillis());
    ScheduledFuture<?> task =
        scheduler.schedule(
            () -> abandonIfStillDisconnected(match.getId()), delayMs, TimeUnit.MILLISECONDS);
    match.setDisconnectTimeoutTask(task);
  }

  private void abandonIfStillDisconnected(UUID matchId) {
    matches
        .findById(matchId)
        .ifPresent(match -> {
          match.getLock().lock();
          try {
            if (match.isTerminal() || !match.areAllPlayersDisconnected()) {
              return;
            }
            match.abandon();
            MatchFinalResult abandonedResult =
                new MatchFinalResult(
                    match.getId(),
                    match.getRoomId(),
                    match.getGameType(),
                    match.getPlayers(),
                    MatchFinalResult.Outcome.ABANDONED,
                    null,
                    "All players disconnected");
            finalizeMatch(match, abandonedResult);
          } finally {
            match.getLock().unlock();
          }
        });
  }

  private void finalizeMatch(Match<?, ?> match, MatchFinalResult finalResult) {
    if (match.claimResultPublication()) {
      resultStore.save(finalResult);
      eventPublisher.publishMatchEnded(finalResult);
      matches.deleteById(match.getId());
    }
  }

  private MatchFinalResult toFinalResult(Match<?, ?> match, MatchResult<String> engineResult) {
    if (engineResult.status() == MatchResult.Status.WIN) {
      return new MatchFinalResult(
          match.getId(),
          match.getRoomId(),
          match.getGameType(),
          match.getPlayers(),
          MatchFinalResult.Outcome.WIN,
          engineResult.winner(),
          "Game completed");
    }
    return new MatchFinalResult(
        match.getId(),
        match.getRoomId(),
        match.getGameType(),
        match.getPlayers(),
        MatchFinalResult.Outcome.DRAW,
        null,
        "Draw");
  }

  private Match<?, ?> getMatchOrThrow(UUID matchId) {
    return matches.findById(matchId).orElseThrow(MatchException::notFound);
  }

  /** Finished matches leave memory, so the result store tells "finished" apart from "unknown". */
  private Match<?, ?> getLiveMatchOrThrow(UUID matchId) {
    return matches
        .findById(matchId)
        .orElseThrow(() ->
            resultStore.existsByMatchId(matchId)
                ? MatchException.matchFinished()
                : MatchException.notFound());
  }

  @Override
  public void destroy() {
    scheduler.shutdownNow();
  }
}
