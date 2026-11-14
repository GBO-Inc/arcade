package com.arcade.backend.lobby;

import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(@NotNull GameType gameType) {}
