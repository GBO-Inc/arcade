package com.arcade.backend;

import com.arcade.backend.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests extends AbstractIntegrationTest {

  @Test
  void contextLoads() {}
}
