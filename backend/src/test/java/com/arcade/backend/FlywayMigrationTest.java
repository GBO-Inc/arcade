package com.arcade.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcade.backend.common.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void deleteUsers() {
    jdbcTemplate.update("delete from users");
  }

  @Test
  void baseMigrationCreatesUsersTableWithAllColumns() {
    var rows =
        jdbcTemplate.queryForList(
            "select id, username, email, password_hash, display_name, avatar_colour, created_at"
                + " from users");

    assertThat(rows).isEmpty();
  }

  @Test
  void usernameAndEmailAreUniqueIgnoringCase() {
    insertUser("Alice", "alice@example.com");

    assertThatThrownBy(() -> insertUser("aLICE", "other@example.com"))
        .isInstanceOf(DuplicateKeyException.class);
    assertThatThrownBy(() -> insertUser("Bob", "ALICE@example.com"))
        .isInstanceOf(DuplicateKeyException.class);
  }

  private void insertUser(String username, String email) {
    jdbcTemplate.update(
        "insert into users (username, email, password_hash, display_name) values (?, ?, 'x', ?)",
        username,
        email,
        username);
  }
}
