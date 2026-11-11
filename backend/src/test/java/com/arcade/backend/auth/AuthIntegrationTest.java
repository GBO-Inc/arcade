package com.arcade.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

  private static final String PASSWORD = "correct horse";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private ObjectMapper objectMapper;

  @AfterEach
  void deleteUsers() {
    jdbcTemplate.update("delete from users");
  }

  @Test
  void registerCreatesUserAndStoresOnlyABcryptHash() throws Exception {
    String body =
        register("Alice", "alice@example.com", PASSWORD)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.username").value("Alice"))
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.displayName").value("Alice"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String hash =
        jdbcTemplate.queryForObject(
            "select password_hash from users where username = 'Alice'", String.class);
    assertThat(hash).startsWith("$2").isNotEqualTo(PASSWORD);
    assertThat(body).doesNotContain(PASSWORD).doesNotContain(hash).doesNotContain("password");
  }

  @Test
  void registerWithInvalidFieldsReturnsFieldErrorsWithoutEchoingThePassword() throws Exception {
    String body =
        register("ab", "not-an-email", "seven77")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(
                jsonPath("$.fieldErrors[*].field")
                    .value(containsInAnyOrder("username", "email", "password")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("seven77");
  }

  @Test
  void registerWithUsernameTakenInAnotherCaseReturnsConflict() throws Exception {
    register("Alice", "alice@example.com", PASSWORD).andExpect(status().isCreated());

    register("aLICE", "other@example.com", PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Username is already taken"));
  }

  @Test
  void registerWithEmailTakenInAnotherCaseReturnsConflict() throws Exception {
    register("Alice", "alice@example.com", PASSWORD).andExpect(status().isCreated());

    register("Bob", "ALICE@example.com", PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Email is already registered"));
  }

  @Test
  void loginReturnsTokenValidFor24HoursThatOpensMe() throws Exception {
    register("Alice", "alice@example.com", PASSWORD).andExpect(status().isCreated());

    JsonNode tokenResponse =
        objectMapper.readTree(
            login("alice", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    String token = tokenResponse.get("accessToken").asText();

    Jwt jwt = jwtDecoder.decode(token);
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofHours(24));
    assertThat(Instant.parse(tokenResponse.get("expiresAt").asText()))
        .isEqualTo(jwt.getExpiresAt());

    mockMvc
        .perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("Alice"))
        .andExpect(jsonPath("$.email").value("alice@example.com"));
  }

  @Test
  void loginWithWrongPasswordOrUnknownUserReturnsTheSameGenericUnauthorized() throws Exception {
    register("Alice", "alice@example.com", PASSWORD).andExpect(status().isCreated());

    login("Alice", "wrong password")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid username or password"));
    login("Nobody", PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid username or password"));
  }

  @Test
  void meWithoutTokenReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("Authentication required"));
  }

  @Test
  void meWithExpiredTokenReturnsUnauthorized() throws Exception {
    long id = registeredUserId();
    Instant issuedAt = Instant.now().minus(Duration.ofHours(25));
    String expired = sign(jwtEncoder, id, issuedAt, issuedAt.plus(Duration.ofHours(24)));

    mockMvc
        .perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Authentication required"));
  }

  @Test
  void meWithTokenSignedByAnotherKeyReturnsUnauthorized() throws Exception {
    long id = registeredUserId();
    var otherKey =
        new SecretKeySpec(
            "another-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");
    Instant now = Instant.now();
    String forged =
        sign(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)), id, now, now.plusSeconds(60));

    mockMvc
        .perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
        .andExpect(status().isUnauthorized());
  }

  private long registeredUserId() throws Exception {
    String body =
        register("Alice", "alice@example.com", PASSWORD)
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  private static String sign(JwtEncoder encoder, long userId, Instant issuedAt, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(String.valueOf(userId))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private ResultActions register(String username, String email, String password) throws Exception {
    return postJson(
        "/api/auth/register",
        objectMapper.writeValueAsString(new RegisterRequest(username, email, password)));
  }

  private ResultActions login(String username, String password) throws Exception {
    return postJson(
        "/api/auth/login", objectMapper.writeValueAsString(new LoginRequest(username, password)));
  }

  private ResultActions postJson(String path, String json) throws Exception {
    return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
  }
}
