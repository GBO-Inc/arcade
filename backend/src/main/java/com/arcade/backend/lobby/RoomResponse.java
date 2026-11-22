package com.arcade.backend.lobby;

public record RoomResponse(
    long id,
    String code,
    GameType gameType,
    RoomStatus status,
    String creatorName,
    int capacity,
    int freeSeats) {}
