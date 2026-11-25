package com.arcade.backend.match;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arcade.backend.AbstractIntegrationTest;
import com.arcade.backend.auth.LoginRequest;
import com.arcade.backend.auth.RegisterRequest;
import com.arcade.backend.lobby.GameType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
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
class MatchControllerIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "correct horse";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MatchService matchService;

  @AfterEach
  void deleteUsers() {
    jdbcTemplate.update("delete from users");
  }

  @Test
  void participantGetsTheMatchState() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");
    Match<?, ?> match = createMatch(alice, bob);

    getMatch(alice.token(), match.getId())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchId").value(match.getId().toString()))
        .andExpect(jsonPath("$.gameType").value("TIC_TAC_TOE"))
        .andExpect(jsonPath("$.status").value("WAITING"))
        .andExpect(jsonPath("$.players.length()").value(2))
        .andExpect(jsonPath("$.moveCount").value(0));
    getMatch(bob.token(), match.getId()).andExpect(status().isOk());
  }

  @Test
  void nonParticipantIsForbidden() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");
    Player carol = signUp("Carol");
    Match<?, ?> match = createMatch(alice, bob);

    getMatch(carol.token(), match.getId())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("You are not a player in this match"));
  }

  @Test
  void unknownMatchIsNotFound() throws Exception {
    Player alice = signUp("Alice");

    getMatch(alice.token(), UUID.randomUUID()).andExpect(status().isNotFound());
  }

  @Test
  void requestWithoutTokenIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/matches/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
  }

  private Match<?, ?> createMatch(Player first, Player second) {
    return matchService.createFromFullRoom(
        1L, GameType.TIC_TAC_TOE, List.of(String.valueOf(first.id()), String.valueOf(second.id())));
  }

  private ResultActions getMatch(String token, UUID matchId) throws Exception {
    return mockMvc.perform(get("/api/matches/" + matchId).header(HttpHeaders.AUTHORIZATION, token));
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
