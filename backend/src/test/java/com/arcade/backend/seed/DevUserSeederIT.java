package com.arcade.backend.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arcade.backend.AbstractIntegrationTest;
import com.arcade.backend.auth.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = "arcade.seed.enabled=true")
class DevUserSeederIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserRepository users;
  @Autowired private DevUserSeeder seeder;

  @BeforeEach
  void startWithoutUsers() {
    jdbcTemplate.update("delete from users");
  }

  @AfterEach
  void deleteUsers() {
    jdbcTemplate.update("delete from users");
  }

  @Test
  void seedCreatesAliceAndBobWithEmailsAndDisplayNames() {
    seeder.run(new DefaultApplicationArguments());

    assertThat(users.findByUsernameIgnoreCase("alice"))
        .hasValueSatisfying(user -> {
          assertThat(user.getEmail()).isEqualTo("alice@example.com");
          assertThat(user.getDisplayName()).isEqualTo("Alice");
        });
    assertThat(users.findByUsernameIgnoreCase("bob"))
        .hasValueSatisfying(user -> {
          assertThat(user.getEmail()).isEqualTo("bob@example.com");
          assertThat(user.getDisplayName()).isEqualTo("Bob");
        });
  }

  @Test
  void seededUsersCanLogInWithTheDevPassword() throws Exception {
    seeder.run(new DefaultApplicationArguments());

    login("alice").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    login("bob").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  @Test
  void runningTheSeederAgainCreatesNoDuplicatesAndDoesNotFail() {
    seeder.run(new DefaultApplicationArguments());
    seeder.run(new DefaultApplicationArguments());

    assertThat(users.count()).isEqualTo(2);
  }

  private ResultActions login(String username) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"arcade-dev-password\"}"));
  }
}
