package com.arcade.backend.lobby;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

  private final RoomService roomService;
  private final RoomEventPublisher roomEventPublisher;

  public RoomController(RoomService roomService, RoomEventPublisher roomEventPublisher) {
    this.roomService = roomService;
    this.roomEventPublisher = roomEventPublisher;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoomResponse create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRoomRequest request) {
    RoomResponse room = roomService.create(userId(jwt), request.gameType());
    roomEventPublisher.publishRoomListChanged();
    return room;
  }

  @GetMapping
  public List<RoomResponse> listOpen() {
    return roomService.listOpen();
  }

  @PostMapping("/join")
  public RoomResponse join(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinRoomRequest request) {
    RoomResponse room = roomService.join(userId(jwt), request.code());
    roomEventPublisher.publishRoomListChanged();
    return room;
  }

  @PostMapping("/{id}/leave")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    roomService.leave(userId(jwt), id);
    roomEventPublisher.publishRoomListChanged();
  }

  private static long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
