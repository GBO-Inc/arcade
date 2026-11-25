package com.arcade.backend.lobby;

public record RoomSeat(long userId, String displayName, String avatarColour, boolean ready) {}
