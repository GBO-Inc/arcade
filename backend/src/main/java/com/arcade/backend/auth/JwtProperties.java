package com.arcade.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("arcade.jwt")
public record JwtProperties(String secret, Duration ttl) {

  // HS256 needs a key of at least 256 bits.
  private static final int MIN_SECRET_BYTES = 32;

  public JwtProperties {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException(
          "arcade.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes");
    }
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("arcade.jwt.ttl must be positive");
    }
  }

  SecretKey secretKey() {
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Override
  public String toString() {
    return "JwtProperties[secret=***, ttl=" + ttl + "]";
  }
}
