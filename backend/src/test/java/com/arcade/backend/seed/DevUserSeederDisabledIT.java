package com.arcade.backend.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcade.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

// The dev profile alone must not seed: the opt-in property is missing in this context.
@ActiveProfiles("dev")
class DevUserSeederDisabledIT extends AbstractIntegrationTest {

  @Autowired private ApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void seederIsAbsentWithoutOptInEvenInTheDevProfile() {
    assertThat(context.getBeansOfType(DevUserSeeder.class)).isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from users where username in ('alice', 'bob')", Integer.class))
        .isZero();
  }
}
