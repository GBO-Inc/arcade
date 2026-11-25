package com.arcade.backend.lobby;

public record RoomListChangedEvent(String type) {

  public RoomListChangedEvent() {
    this("ROOM_LIST_CHANGED");
  }
}
