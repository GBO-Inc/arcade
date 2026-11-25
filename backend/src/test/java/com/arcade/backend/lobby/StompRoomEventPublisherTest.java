package com.arcade.backend.lobby;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class StompRoomEventPublisherTest {

  @Test
  void publishesRoomListChangedEventToRoomTopic() {
    SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    StompRoomEventPublisher publisher = new StompRoomEventPublisher(messagingTemplate);

    publisher.publishRoomListChanged();

    verify(messagingTemplate)
        .convertAndSend("/topic/rooms", new RoomListChangedEvent("ROOM_LIST_CHANGED"));
  }
}
