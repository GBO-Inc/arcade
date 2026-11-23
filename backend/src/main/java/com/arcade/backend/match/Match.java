package com.arcade.backend.match;

import com.arcade.backend.lobby.GameType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class Match<TState, TMove> {

  private final UUID id;
  private final long roomId;
  private final GameType gameType;
  private final List<String> players;
  private final MatchStateMachine stateMachine;
  private final Set<String> connectedPlayers = new HashSet<>();
  private final List<TMove> moves = new ArrayList<>();

  private TState state;
  private String currentTurnPlayerId;

  public Match(
      UUID id, long roomId, GameType gameType, List<String> orderedPlayers, TState initialState) {
    if (orderedPlayers == null || orderedPlayers.size() != 2) {
      throw new IllegalArgumentException("Exactly 2 players are required");
    }
    this.id = id;
    this.roomId = roomId;
    this.gameType = gameType;
    this.players = List.copyOf(orderedPlayers);
    this.stateMachine = new MatchStateMachine();
    this.state = initialState;
    this.currentTurnPlayerId = orderedPlayers.get(0);
  }

  public UUID getId() {
    return id;
  }

  public long getRoomId() {
    return roomId;
  }

  public GameType getGameType() {
    return gameType;
  }

  public List<String> getPlayers() {
    return players;
  }

  public MatchStatus getStatus() {
    return stateMachine.getCurrentState();
  }

  public TState getState() {
    return state;
  }

  public String getCurrentTurnPlayerId() {
    return currentTurnPlayerId;
  }

  public List<TMove> getMoves() {
    return Collections.unmodifiableList(moves);
  }

  public Set<String> getConnectedPlayers() {
    return Collections.unmodifiableSet(connectedPlayers);
  }

  public void markPlayerConnected(String playerId) {
    if (!players.contains(playerId)) {
      throw new IllegalArgumentException("Player is not part of this match: " + playerId);
    }
    connectedPlayers.add(playerId);
    if (stateMachine.getCurrentState() == MatchStatus.WAITING
        && connectedPlayers.containsAll(players)) {
      stateMachine.transitionTo(MatchStatus.IN_PROGRESS);
    }
  }

  public void abandon() {
    stateMachine.transitionTo(MatchStatus.ABANDONED);
  }

  public void recordMove(TMove move, TState newState) {
    this.moves.add(move);
    this.state = newState;
    this.currentTurnPlayerId =
        players.get(0).equals(currentTurnPlayerId) ? players.get(1) : players.get(0);
  }
}
