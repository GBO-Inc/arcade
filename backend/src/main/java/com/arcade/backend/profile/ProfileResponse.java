package com.arcade.backend.profile;

import com.arcade.backend.auth.User;
import java.time.Instant;

/** What any signed-in player may see about another: no email, no password hash. */
public record ProfileResponse(Long id, String displayName, String avatarColour, Instant createdAt) {

  static ProfileResponse from(User user) {
    return new ProfileResponse(
        user.getId(), user.getDisplayName(), user.getAvatarColour(), user.getCreatedAt());
  }
}
