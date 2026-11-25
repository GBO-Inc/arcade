package com.arcade.backend.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcade.backend.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// The opt-in property alone must not seed: a non-dev profile is active in this context.
@ActiveProfiles("prod")
@TestPropertySource(properties = "arcade.seed.enabled=true")
class DevUserSeederProfileIT extends AbstractIntegrationTest {

  @Autowired private ApplicationContext context;

  @Test
  void seederIsAbsentOutsideTheDevProfileEvenWhenOptedIn() {
    assertThat(context.getBeansOfType(DevUserSeeder.class)).isEmpty();
  }
}
