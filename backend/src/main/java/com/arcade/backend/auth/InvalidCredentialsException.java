package com.arcade.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Same message for unknown user and wrong password, so login does not reveal which it was. */
public class InvalidCredentialsException extends ErrorResponseException {

  public InvalidCredentialsException() {
    super(
        HttpStatus.UNAUTHORIZED,
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
        null);
  }
}
