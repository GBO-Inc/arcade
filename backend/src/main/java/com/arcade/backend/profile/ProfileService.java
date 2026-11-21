package com.arcade.backend.profile;

import com.arcade.backend.auth.AccountNotFoundException;
import com.arcade.backend.auth.User;
import com.arcade.backend.auth.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

  private final UserRepository users;

  public ProfileService(UserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public ProfileResponse get(long userId) {
    return users
        .findById(userId)
        .map(ProfileResponse::from)
        .orElseThrow(ProfileException::notFound);
  }

  @Transactional
  public ProfileResponse update(
      long currentUserId, long targetUserId, ProfileUpdateRequest request) {
    if (currentUserId != targetUserId) {
      throw ProfileException.notYours();
    }
    User user = users.findById(currentUserId).orElseThrow(AccountNotFoundException::new);
    user.updateProfile(request.displayName(), request.avatarColour());
    return ProfileResponse.from(user);
  }
}
