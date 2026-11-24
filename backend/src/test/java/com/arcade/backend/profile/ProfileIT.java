package com.arcade.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arcade.backend.AbstractIntegrationTest;
import com.arcade.backend.auth.LoginRequest;
import com.arcade.backend.auth.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class ProfileIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "correct horse";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  @AfterEach
  void deleteUsers() {
    jdbcTemplate.update("delete from users");
  }

  @Test
  void getReturnsPublicFieldsOnlyForAnyUser() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");

    String body =
        getProfile(bob.token(), alice.id())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(alice.id()))
            .andExpect(jsonPath("$.displayName").value("Alice"))
            .andExpect(jsonPath("$.createdAt").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode json = objectMapper.readTree(body);
    assertThat(json.has("avatarColour")).isTrue();
    assertThat(Instant.parse(json.get("createdAt").asText())).isBefore(Instant.now());
    assertThat(body)
        .doesNotContain("alice@example.com")
        .doesNotContain("email")
        .doesNotContain("password")
        .doesNotContain("$2");
  }

  @Test
  void getUnknownUserReturnsNotFound() throws Exception {
    Player alice = signUp("Alice");

    getProfile(alice.token(), alice.id() + 1000).andExpect(status().isNotFound());
  }

  @Test
  void getWithoutTokenReturnsUnauthorized() throws Exception {
    Player alice = signUp("Alice");

    mockMvc.perform(get("/api/profile/" + alice.id())).andExpect(status().isUnauthorized());
  }

  @Test
  void updateTrimsAndSavesOwnProfile() throws Exception {
    Player alice = signUp("Alice");

    updateProfile(alice.token(), "/api/profile", "  Queen Alice  ", "#3B82F6")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(alice.id()))
        .andExpect(jsonPath("$.displayName").value("Queen Alice"))
        .andExpect(jsonPath("$.avatarColour").value("#3B82F6"));

    getProfile(alice.token(), alice.id())
        .andExpect(jsonPath("$.displayName").value("Queen Alice"))
        .andExpect(jsonPath("$.avatarColour").value("#3B82F6"));
  }

  @Test
  void updateByIdOfOwnProfileIsAllowed() throws Exception {
    Player alice = signUp("Alice");

    updateProfile(alice.token(), "/api/profile/" + alice.id(), "Al", "#EF4444")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Al"));
  }

  @Test
  void updateOfAnotherUsersProfileIsForbiddenAndChangesNothing() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");

    updateProfile(bob.token(), "/api/profile/" + alice.id(), "Hacked", "#EF4444")
        .andExpect(status().isForbidden());

    getProfile(alice.token(), alice.id()).andExpect(jsonPath("$.displayName").value("Alice"));
  }

  @Test
  void updateRejectsNamesOutsideTwoToTwentyCharactersAfterTrimming() throws Exception {
    Player alice = signUp("Alice");

    updateProfile(alice.token(), "/api/profile", "  A  ", "#3B82F6")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("displayName")));
    updateProfile(alice.token(), "/api/profile", "A".repeat(21), "#3B82F6")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("displayName")));
    updateProfile(alice.token(), "/api/profile", "A".repeat(20), "#3B82F6")
        .andExpect(status().isOk());
  }

  @Test
  void updateRejectsColoursOutsideThePaletteAndMissingFields() throws Exception {
    Player alice = signUp("Alice");

    updateProfile(alice.token(), "/api/profile", "Alice", "#123456")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("avatarColour")));
    updateProfile(alice.token(), "/api/profile", null, null)
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.fieldErrors[*].field")
                .value(containsInAnyOrder("displayName", "avatarColour")));

    getProfile(alice.token(), alice.id()).andExpect(jsonPath("$.displayName").value("Alice"));
  }

  private ResultActions getProfile(String token, long userId) throws Exception {
    return mockMvc.perform(get("/api/profile/" + userId).header(HttpHeaders.AUTHORIZATION, token));
  }

  private ResultActions updateProfile(
      String token, String path, String displayName, String avatarColour) throws Exception {
    return mockMvc.perform(
        put(path)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    new ProfileUpdateRequest(displayName, avatarColour))));
  }

  private record Player(long id, String token) {}

  private Player signUp(String username) throws Exception {
    String registered =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RegisterRequest(
                                username, username.toLowerCase() + "@example.com", PASSWORD))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String login =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Player(
        objectMapper.readTree(registered).get("id").asLong(),
        "Bearer " + objectMapper.readTree(login).get("accessToken").asText());
  }
}
