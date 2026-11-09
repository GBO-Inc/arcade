package com.arcade.backend.auth;

import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final Clock clock;

  public AuthService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      TokenService tokenService,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.clock = clock;
  }

  @Transactional
  public User register(RegisterRequest request) {
    if (users.existsByUsernameIgnoreCase(request.username())) {
      throw DuplicateAccountException.username();
    }
    if (users.existsByEmailIgnoreCase(request.email())) {
      throw DuplicateAccountException.email();
    }
    User user =
        new User(
            request.username(),
            request.email(),
            passwordEncoder.encode(request.password()),
            request.username(),
            Instant.now(clock));
    try {
      return users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw DuplicateAccountException.concurrent();
    }
  }

  @Transactional(readOnly = true)
  public TokenResponse login(LoginRequest request) {
    User user =
        users
            .findByUsernameIgnoreCase(request.username())
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            .orElseThrow(InvalidCredentialsException::new);
    return tokenService.issue(user);
  }

  @Transactional(readOnly = true)
  public User currentUser(long userId) {
    return users.findById(userId).orElseThrow(AccountNotFoundException::new);
  }
}
