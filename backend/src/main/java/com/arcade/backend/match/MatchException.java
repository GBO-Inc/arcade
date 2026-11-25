package com.arcade.backend.match;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class MatchException extends ErrorResponseException {

  private final String code;

  private MatchException(HttpStatus status, String code, String message) {
    super(status, ProblemDetail.forStatusAndDetail(status, message), null);
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static MatchException notFound() {
    return new MatchException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "Match not found");
  }

  public static MatchException notParticipant() {
    return new MatchException(
        HttpStatus.FORBIDDEN, "NOT_PARTICIPANT", "You are not a player in this match");
  }

  public static MatchException notInProgress() {
    return new MatchException(
        HttpStatus.CONFLICT, "MATCH_NOT_IN_PROGRESS", "Match is not in progress");
  }

  public static MatchException matchFinished() {
    return new MatchException(HttpStatus.CONFLICT, "MATCH_FINISHED", "Match has already finished");
  }

  public static MatchException notYourTurn() {
    return new MatchException(HttpStatus.CONFLICT, "NOT_YOUR_TURN", "Not your turn");
  }

  public static MatchException invalidMove(String reason) {
    return new MatchException(
        HttpStatus.BAD_REQUEST, "INVALID_MOVE", reason != null ? reason : "Invalid move");
  }
}
