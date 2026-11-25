package com.arcade.backend.lobby;

import java.util.List;
import java.util.UUID;

/**
 * What a player inside a room sees: who sits where and who is ready. {@code matchId} is set only
 * once the room has started, so a client that missed the start event can still find its match.
 */
public record RoomDetail(
    long id,
    String code,
    GameType gameType,
    RoomStatus status,
    long creatorId,
    int capacity,
    UUID matchId,
    List<RoomSeat> seats) {}
