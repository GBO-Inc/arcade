package com.arcade.backend.lobby;

import java.util.List;

/** What a player inside a room sees: who sits where and who is ready. */
public record RoomDetail(
    long id,
    String code,
    GameType gameType,
    RoomStatus status,
    long creatorId,
    int capacity,
    List<RoomSeat> seats) {}
