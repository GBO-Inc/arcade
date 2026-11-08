package com.arcade.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A validly signed token whose user no longer exists. */
public class AccountNotFoundException extends ErrorResponseException {

  public AccountNotFoundException() {
    super(
        HttpStatus.UNAUTHORIZED,
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required"),
        null);
  }
}
