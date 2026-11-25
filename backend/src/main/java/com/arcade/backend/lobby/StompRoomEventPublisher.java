package com.arcade.backend.lobby;

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
}
