package com.arcade.backend.match;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class MatchException extends ErrorResponseException {

  private MatchException(HttpStatus status, String message) {
    super(status, ProblemDetail.forStatusAndDetail(status, message), null);
  }

  public static MatchException notFound() {
    return new MatchException(HttpStatus.NOT_FOUND, "Match not found");
  }

  public static MatchException notParticipant() {
    return new MatchException(HttpStatus.FORBIDDEN, "You are not a player in this match");
  }

  public static MatchException notInProgress() {
    return new MatchException(HttpStatus.CONFLICT, "Match is not in progress");
  }

  public static MatchException notYourTurn() {
    return new MatchException(HttpStatus.CONFLICT, "Not your turn");
  }

  public static MatchException invalidMove(String reason) {
    return new MatchException(HttpStatus.BAD_REQUEST, reason != null ? reason : "Invalid move");
  }
}
