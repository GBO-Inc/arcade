package com.arcade.backend.lobby;

import jakarta.validation.constraints.NotBlank;

public record JoinRoomRequest(@NotBlank String code) {}
