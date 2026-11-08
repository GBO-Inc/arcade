package com.arcade.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void baseMigrationCreatesUsersTableWithAllColumns() {
    var rows =
        jdbcTemplate.queryForList(
            "select id, username, email, password_hash, display_name, avatar_colour, created_at"
                + " from users");

    assertThat(rows).isEmpty();
  }
}
