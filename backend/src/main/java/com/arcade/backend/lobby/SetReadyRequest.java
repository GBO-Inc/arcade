package com.arcade.backend.lobby;

import jakarta.validation.constraints.NotNull;

public record SetReadyRequest(@NotNull Boolean ready) {}
