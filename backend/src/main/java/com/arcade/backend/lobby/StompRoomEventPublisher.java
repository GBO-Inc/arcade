package com.arcade.backend.lobby;

import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class StompRoomEventPublisher implements RoomEventPublisher {

  private final SimpMessagingTemplate messagingTemplate;

  public StompRoomEventPublisher(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @Override
  public void publishRoomListChanged() {
    messagingTemplate.convertAndSend("/topic/rooms", new RoomListChangedEvent());
  }

  @Override
  public void publishRoomUpdated(RoomDetail room) {
    messagingTemplate.convertAndSend(roomTopic(room.id()), new RoomUpdatedEvent(room));
  }

  @Override
  public void publishMatchStarted(long roomId, UUID matchId) {
    messagingTemplate.convertAndSend(roomTopic(roomId), new MatchStartedEvent(matchId));
  }

  @Override
  public void publishRoomClosed(long roomId) {
    messagingTemplate.convertAndSend(roomTopic(roomId), new RoomClosedEvent());
  }

  private static String roomTopic(long roomId) {
    return "/topic/rooms/" + roomId;
  }
}
