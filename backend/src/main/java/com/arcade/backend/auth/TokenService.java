package com.arcade.backend.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

  private final JwtEncoder jwtEncoder;
  private final JwtProperties properties;
  private final Clock clock;

  public TokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
    this.clock = clock;
  }

  /** The token subject is the user id, which never changes (unlike the display name). */
  public TokenResponse issue(User user) {
    // JWT times are whole seconds; truncating keeps expiresAt in the response equal to the token.
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
    Instant expiresAt = now.plus(properties.ttl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(String.valueOf(user.getId()))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new TokenResponse(token, "Bearer", expiresAt);
  }
}
