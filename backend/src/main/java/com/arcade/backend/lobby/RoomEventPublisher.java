package com.arcade.backend.lobby;

import java.util.UUID;

public interface RoomEventPublisher {
  void publishRoomListChanged();

  void publishRoomUpdated(RoomDetail room);

  void publishMatchStarted(long roomId, UUID matchId);

  void publishRoomClosed(long roomId);
}
