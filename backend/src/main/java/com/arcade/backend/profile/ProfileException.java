package com.arcade.backend.profile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ProfileException extends ErrorResponseException {

  private ProfileException(HttpStatus status, String message) {
    super(status, ProblemDetail.forStatusAndDetail(status, message), null);
  }

  static ProfileException notFound() {
    return new ProfileException(HttpStatus.NOT_FOUND, "Player not found");
  }

  static ProfileException notYours() {
    return new ProfileException(HttpStatus.FORBIDDEN, "You can only edit your own profile");
  }
}
