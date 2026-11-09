package com.arcade.backend.engine.dummy;

import java.util.Map;

public record DummyState(Map<String, Integer> scores, String currentPlayer, String waitingPlayer) {}
