package com.arcade.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 20) String username,
    @NotBlank @Email @Size(max = 255) String email,
    // BCrypt only uses the first 72 bytes of a password.
    @NotNull @Size(min = 8, max = 72) String password) {

  @Override
  public String toString() {
    return "RegisterRequest[username=" + username + ", email=" + email + ", password=***]";
  }
}
