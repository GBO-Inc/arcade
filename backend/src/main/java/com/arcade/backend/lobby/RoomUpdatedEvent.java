package com.arcade.backend.lobby;

public record RoomUpdatedEvent(String type, RoomDetail room) {

  public RoomUpdatedEvent(RoomDetail room) {
    this("ROOM_UPDATED", room);
  }
}
