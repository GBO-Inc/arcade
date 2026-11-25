package com.arcade.backend.seed;

import com.arcade.backend.auth.AuthService;
import com.arcade.backend.auth.RegisterRequest;
import com.arcade.backend.auth.User;
import com.arcade.backend.auth.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the demo accounts alice and bob when the server starts.
 *
 * <p>Two guards on purpose. The dev profile alone is not enough: application.properties makes dev
 * the default profile, so a server started without any profile would seed. The opt-in property
 * defaults to off and is set only by the local dev reset script (ARCADE_SEED_ENABLED=true).
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "arcade.seed.enabled", havingValue = "true")
public class DevUserSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

  // Known password for local demo logins only. It is documented in the README.
  private static final String DEV_PASSWORD = "arcade-dev-password";

  private static final List<DemoUser> DEMO_USERS =
      List.of(
          new DemoUser("alice", "alice@example.com", "Alice", "#EF4444"),
          new DemoUser("bob", "bob@example.com", "Bob", "#3B82F6"));

  private final AuthService authService;
  private final UserRepository users;

  public DevUserSeeder(AuthService authService, UserRepository users) {
    this.authService = authService;
    this.users = users;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<String> created = new ArrayList<>();
    List<String> existing = new ArrayList<>();
    for (DemoUser demo : DEMO_USERS) {
      if (users.findByUsernameIgnoreCase(demo.username()).isPresent()) {
        existing.add(demo.username());
      } else {
        createDemoUser(demo);
        created.add(demo.username());
      }
    }
    log.info("Dev seed: created {}, already present {}", created, existing);
  }

  // Goes through the same registration path as real sign-ups, so the password is hashed the
  // same way and login works. Registration sets the display name to the username, so it is
  // changed afterwards to the demo display name.
  private void createDemoUser(DemoUser demo) {
    User user =
        authService.register(new RegisterRequest(demo.username(), demo.email(), DEV_PASSWORD));
    user.updateProfile(demo.displayName(), demo.avatarColour());
  }

  private record DemoUser(String username, String email, String displayName, String avatarColour) {}
}
