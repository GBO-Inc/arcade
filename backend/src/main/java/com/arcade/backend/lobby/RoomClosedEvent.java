package com.arcade.backend.lobby;

public record RoomClosedEvent(String type) {

  public RoomClosedEvent() {
    this("ROOM_CLOSED");
  }
}
