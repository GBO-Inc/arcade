package com.arcade.backend.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.oauth2.jwt.Jwt;

class RoomControllerTest {

  private RoomService roomService;
  private RoomEventPublisher roomEventPublisher;
  private RoomController controller;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    roomService = mock(RoomService.class);
    roomEventPublisher = mock(RoomEventPublisher.class);
    controller = new RoomController(roomService, roomEventPublisher);
    jwt = Jwt.withTokenValue("token").header("alg", "none").subject("7").build();
  }

  @Test
  void publishesRoomListChangeAfterSuccessfulCreate() {
    RoomResponse room = room();
    when(roomService.create(7L, GameType.TIC_TAC_TOE)).thenReturn(room);

    assertThat(controller.create(jwt, new CreateRoomRequest(GameType.TIC_TAC_TOE))).isEqualTo(room);

    InOrder order = inOrder(roomService, roomEventPublisher);
    order.verify(roomService).create(7L, GameType.TIC_TAC_TOE);
    order.verify(roomEventPublisher).publishRoomListChanged();
  }

  @Test
  void publishesRoomListChangeAfterSuccessfulJoin() {
    RoomResponse room = room();
    when(roomService.join(7L, "ABC234")).thenReturn(room);

    assertThat(controller.join(jwt, new JoinRoomRequest("ABC234"))).isEqualTo(room);

    InOrder order = inOrder(roomService, roomEventPublisher);
    order.verify(roomService).join(7L, "ABC234");
    order.verify(roomEventPublisher).publishRoomListChanged();
  }

  @Test
  void publishesRoomListChangeAfterSuccessfulLeave() {
    controller.leave(jwt, 42L);

    InOrder order = inOrder(roomService, roomEventPublisher);
    order.verify(roomService).leave(7L, 42L);
    order.verify(roomEventPublisher).publishRoomListChanged();
  }

  @Test
  void publishesRoomListChangeAfterSuccessfulClose() {
    controller.close(jwt, 42L);

    InOrder order = inOrder(roomService, roomEventPublisher);
    order.verify(roomService).close(7L, 42L);
    order.verify(roomEventPublisher).publishRoomListChanged();
  }

  @Test
  void readyDelegatesToTheServiceAndLeavesPublishingToIt() {
    RoomDetail detail =
        new RoomDetail(
            42L, "ABC234", GameType.TIC_TAC_TOE, RoomStatus.FULL, 7L, 2, null, List.of());
    when(roomService.setReady(7L, 42L, true)).thenReturn(detail);

    assertThat(controller.setReady(jwt, 42L, new SetReadyRequest(true))).isEqualTo(detail);

    verifyNoInteractions(roomEventPublisher);
  }

  @Test
  void doesNotPublishWhenCloseFails() {
    org.mockito.Mockito.doThrow(RoomException.notCreator()).when(roomService).close(7L, 42L);

    assertThatThrownBy(() -> controller.close(jwt, 42L)).isInstanceOf(RoomException.class);

    verifyNoInteractions(roomEventPublisher);
  }

  @Test
  void doesNotPublishWhenCreateFails() {
    when(roomService.create(7L, GameType.TIC_TAC_TOE))
        .thenThrow(new IllegalStateException("Create failed"));

    assertThatThrownBy(() -> controller.create(jwt, new CreateRoomRequest(GameType.TIC_TAC_TOE)))
        .isInstanceOf(IllegalStateException.class);

    verifyNoInteractions(roomEventPublisher);
  }

  @Test
  void doesNotPublishWhenJoinFails() {
    when(roomService.join(7L, "XYZ234")).thenThrow(RoomException.unknownCode());

    assertThatThrownBy(() -> controller.join(jwt, new JoinRoomRequest("XYZ234")))
        .isInstanceOf(RoomException.class);

    verifyNoInteractions(roomEventPublisher);
  }

  @Test
  void doesNotPublishWhenLeaveFails() {
    org.mockito.Mockito.doThrow(RoomException.roomNotFound()).when(roomService).leave(7L, 42L);

    assertThatThrownBy(() -> controller.leave(jwt, 42L)).isInstanceOf(RoomException.class);

    verifyNoInteractions(roomEventPublisher);
  }

  private static RoomResponse room() {
    return new RoomResponse(42L, "ABC234", GameType.TIC_TAC_TOE, RoomStatus.OPEN, "Alice", 2, 1);
  }
}
