package com.arcade.backend.auth;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {}
