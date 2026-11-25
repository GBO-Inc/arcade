package com.arcade.backend.match;

public record MatchMoveEvent(MatchStateResponse match, Object move, String movedBy) {}
