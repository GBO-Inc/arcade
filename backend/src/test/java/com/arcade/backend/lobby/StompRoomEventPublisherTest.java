package com.arcade.backend.lobby;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class StompRoomEventPublisherTest {

  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final StompRoomEventPublisher publisher = new StompRoomEventPublisher(messagingTemplate);

  @Test
  void publishesRoomListChangedEventToRoomTopic() {
    publisher.publishRoomListChanged();

    verify(messagingTemplate)
        .convertAndSend("/topic/rooms", new RoomListChangedEvent("ROOM_LIST_CHANGED"));
  }

  @Test
  void publishesRoomUpdatesToThePerRoomTopic() {
    RoomDetail room =
        new RoomDetail(
            42L,
            "ABC234",
            GameType.TIC_TAC_TOE,
            RoomStatus.FULL,
            7L,
            2,
            List.of(new RoomSeat(7L, "Alice", "#EF4444", true)));

    publisher.publishRoomUpdated(room);

    verify(messagingTemplate)
        .convertAndSend("/topic/rooms/42", new RoomUpdatedEvent("ROOM_UPDATED", room));
  }

  @Test
  void publishesMatchStartedToThePerRoomTopic() {
    UUID matchId = UUID.randomUUID();

    publisher.publishMatchStarted(42L, matchId);

    verify(messagingTemplate)
        .convertAndSend("/topic/rooms/42", new MatchStartedEvent("MATCH_STARTED", matchId));
  }

  @Test
  void publishesRoomClosedToThePerRoomTopic() {
    publisher.publishRoomClosed(42L);

    verify(messagingTemplate).convertAndSend("/topic/rooms/42", new RoomClosedEvent("ROOM_CLOSED"));
  }
}
