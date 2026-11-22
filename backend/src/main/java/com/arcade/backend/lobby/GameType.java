package com.arcade.backend.lobby;

/** The games a room can be created for. Every game listed so far is a two-player game. */
public enum GameType {
  TIC_TAC_TOE(2),
  CONNECT_FOUR(2),
  CHECKERS(2);

  private final int seats;

  GameType(int seats) {
    this.seats = seats;
  }

  public int seats() {
    return seats;
  }
}
