package com.arcade.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");
  private static final String PASSWORD = "correct horse";

  // Low cost factor keeps the unit tests fast; production uses the default.
  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

  @Mock private UserRepository users;
  @Mock private TokenService tokenService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(users, passwordEncoder, tokenService, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void registerStoresHashedPasswordAndUsesUsernameAsDisplayName() {
    when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

    User user = authService.register(new RegisterRequest("Alice", "alice@example.com", PASSWORD));

    assertThat(user.getUsername()).isEqualTo("Alice");
    assertThat(user.getEmail()).isEqualTo("alice@example.com");
    assertThat(user.getDisplayName()).isEqualTo("Alice");
    assertThat(user.getCreatedAt()).isEqualTo(NOW);
    assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
    assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
  }

  @Test
  void registerRejectsTakenUsername() {
    when(users.existsByUsernameIgnoreCase("Alice")).thenReturn(true);

    assertThatThrownBy(
            () -> authService.register(new RegisterRequest("Alice", "a@example.com", PASSWORD)))
        .isInstanceOfSatisfying(
            DuplicateAccountException.class,
            e -> {
              assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getBody().getDetail()).isEqualTo("Username is already taken");
            });
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void registerRejectsTakenEmail() {
    when(users.existsByEmailIgnoreCase("a@example.com")).thenReturn(true);

    assertThatThrownBy(
            () -> authService.register(new RegisterRequest("Alice", "a@example.com", PASSWORD)))
        .isInstanceOfSatisfying(
            DuplicateAccountException.class,
            e -> assertThat(e.getBody().getDetail()).isEqualTo("Email is already registered"));
    verify(users, never()).saveAndFlush(any());
  }

  @Test
  void registerTurnsAConcurrentDuplicateIntoConflict() {
    when(users.saveAndFlush(any(User.class)))
        .thenThrow(new DataIntegrityViolationException("users_username_lower_key"));

    assertThatThrownBy(
            () -> authService.register(new RegisterRequest("Alice", "a@example.com", PASSWORD)))
        .isInstanceOfSatisfying(
            DuplicateAccountException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  void loginIssuesTokenForCorrectPassword() {
    User alice = storedUser(7L, "Alice", PASSWORD);
    var token = new TokenResponse("token", "Bearer", NOW.plusSeconds(60));
    when(users.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(alice));
    when(tokenService.issue(alice)).thenReturn(token);

    assertThat(authService.login(new LoginRequest("alice", PASSWORD))).isEqualTo(token);
  }

  @Test
  void loginRejectsWrongPasswordAndUnknownUserWithTheSameMessage() {
    when(users.findByUsernameIgnoreCase("Alice"))
        .thenReturn(Optional.of(storedUser(7L, "Alice", PASSWORD)));
    when(users.findByUsernameIgnoreCase("Nobody")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(new LoginRequest("Alice", "wrong password")))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED)
        .satisfies(e ->
            assertThat(((InvalidCredentialsException) e).getBody().getDetail())
                .isEqualTo("Invalid username or password"));
    assertThatThrownBy(() -> authService.login(new LoginRequest("Nobody", PASSWORD)))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(tokenService, never()).issue(any());
  }

  @Test
  void currentUserOfUnknownIdIsUnauthorized() {
    when(users.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.currentUser(99L))
        .isInstanceOfSatisfying(
            AccountNotFoundException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
  }

  @Test
  void requestsDoNotPrintThePassword() {
    assertThat(new RegisterRequest("Alice", "a@example.com", PASSWORD).toString())
        .doesNotContain(PASSWORD);
    assertThat(new LoginRequest("Alice", PASSWORD).toString()).doesNotContain(PASSWORD);
  }

  private User storedUser(long id, String username, String password) {
    User user =
        new User(
            username, username + "@example.com", passwordEncoder.encode(password), username, NOW);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
