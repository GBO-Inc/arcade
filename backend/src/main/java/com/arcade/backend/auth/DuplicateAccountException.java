package com.arcade.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class DuplicateAccountException extends ErrorResponseException {

  private DuplicateAccountException(String message) {
    super(
        HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message), null);
  }

  static DuplicateAccountException username() {
    return new DuplicateAccountException("Username is already taken");
  }

  static DuplicateAccountException email() {
    return new DuplicateAccountException("Email is already registered");
  }

  /** Another registration with the same username or email won the race to the database. */
  static DuplicateAccountException concurrent() {
    return new DuplicateAccountException("Username or email is already taken");
  }
}
