package com.arcade.backend.lobby;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class RoomException extends ErrorResponseException {

  private RoomException(HttpStatus status, String message) {
    super(status, ProblemDetail.forStatusAndDetail(status, message), null);
  }

  static RoomException unknownCode() {
    return new RoomException(HttpStatus.NOT_FOUND, "No open room with that code");
  }

  static RoomException roomNotFound() {
    return new RoomException(HttpStatus.NOT_FOUND, "Room not found");
  }

  static RoomException notInRoom() {
    return new RoomException(HttpStatus.NOT_FOUND, "You are not in that room");
  }

  static RoomException full() {
    return new RoomException(HttpStatus.CONFLICT, "Room is full");
  }

  static RoomException alreadyInRoom() {
    return new RoomException(HttpStatus.CONFLICT, "You are already in another room");
  }

  static RoomException started() {
    return new RoomException(HttpStatus.CONFLICT, "The game in this room has already started");
  }
}
