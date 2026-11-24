package com.arcade.backend.match;

import java.util.UUID;

public record MatchErrorMessage(String code, String message, UUID matchId) {}
