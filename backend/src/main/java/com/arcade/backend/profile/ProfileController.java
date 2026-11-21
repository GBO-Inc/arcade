package com.arcade.backend.profile;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

  private final ProfileService profileService;

  public ProfileController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping("/{userId}")
  public ProfileResponse get(@PathVariable long userId) {
    return profileService.get(userId);
  }

  @PutMapping
  public ProfileResponse updateOwn(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
    long userId = userId(jwt);
    return profileService.update(userId, userId, request);
  }

  // Lets a client address a profile by id; anyone but the owner gets 403.
  @PutMapping("/{userId}")
  public ProfileResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long userId,
      @Valid @RequestBody ProfileUpdateRequest request) {
    return profileService.update(userId(jwt), userId, request);
  }

  private static long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
