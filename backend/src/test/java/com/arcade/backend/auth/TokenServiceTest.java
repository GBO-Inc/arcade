package com.arcade.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

  private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes";

  private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(24));
  private final JwtConfig jwtConfig = new JwtConfig();

  @Test
  void issuedTokenNamesTheUserAndExpiresAfter24Hours() {
    Instant clockTime = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusMillis(750);
    Instant now = clockTime.truncatedTo(ChronoUnit.SECONDS);
    var tokenService =
        new TokenService(
            jwtConfig.jwtEncoder(properties), properties, Clock.fixed(clockTime, ZoneOffset.UTC));
    User user = new User("Alice", "alice@example.com", "hash", "Alice", now);
    ReflectionTestUtils.setField(user, "id", 7L);

    TokenResponse token = tokenService.issue(user);

    assertThat(token.tokenType()).isEqualTo("Bearer");
    assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
    Jwt jwt = jwtConfig.jwtDecoder(properties).decode(token.accessToken());
    assertThat(jwt.getSubject()).isEqualTo("7");
    assertThat(jwt.getIssuedAt()).isEqualTo(now);
    assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
  }

  @Test
  void secretShorterThan32BytesIsRejected() {
    assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(24)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("too-short");
  }

  @Test
  void propertiesDoNotPrintTheSecret() {
    assertThat(properties.toString()).doesNotContain(SECRET);
  }
}
