package com.arcade.backend.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
    @NotBlank @Size(min = 2, max = 20) String displayName,
    @NotNull @PaletteColour String avatarColour) {

  // Trimmed before validation, so "  A  " is one character and rejected.
  public ProfileUpdateRequest {
    displayName = displayName == null ? null : displayName.strip();
  }
}
