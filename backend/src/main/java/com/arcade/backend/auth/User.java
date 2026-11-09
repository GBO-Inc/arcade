package com.arcade.backend.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String username;
  private String email;
  private String passwordHash;
  private String displayName;
  private String avatarColour;
  private Instant createdAt;

  protected User() {}

  public User(
      String username, String email, String passwordHash, String displayName, Instant createdAt) {
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getAvatarColour() {
    return avatarColour;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
