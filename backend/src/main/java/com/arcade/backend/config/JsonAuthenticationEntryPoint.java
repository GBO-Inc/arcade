package com.arcade.backend.config;

import com.arcade.backend.common.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers a missing, expired or invalid token with the same JSON error body as the rest of the API.
 * The message is generic on purpose: it does not say why the token was rejected.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final Clock clock;

  public JsonAuthenticationEntryPoint(ObjectMapper objectMapper, Clock clock) {
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    HttpStatus status = HttpStatus.UNAUTHORIZED;
    response.setStatus(status.value());
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ErrorResponse body =
        new ErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            "Authentication required",
            null);
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
