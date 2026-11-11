package com.arcade.backend.engine;

public record MatchResult<TPlayer>(Status status, TPlayer winner) {

  public enum Status {
    ONGOING,
    WIN,
    DRAW
  }

  public static <TPlayer> MatchResult<TPlayer> ongoing() {
    return new MatchResult<>(Status.ONGOING, null);
  }

  public static <TPlayer> MatchResult<TPlayer> draw() {
    return new MatchResult<>(Status.DRAW, null);
  }

  public static <TPlayer> MatchResult<TPlayer> win(TPlayer winner) {
    return new MatchResult<>(Status.WIN, winner);
  }
}
