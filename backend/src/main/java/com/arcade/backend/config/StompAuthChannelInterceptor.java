package com.arcade.backend.config;

import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";
  private final JwtDecoder jwtDecoder;

  public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    switch (accessor.getCommand()) {
      case CONNECT -> authenticateConnect(accessor);
      case SUBSCRIBE -> validateSubscription(accessor);
      case SEND -> requireAuthenticated(accessor);
      default -> {}
    }

    return message;
  }

  private void authenticateConnect(StompHeaderAccessor accessor) {
    String authHeader = accessor.getFirstNativeHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      throw new BadCredentialsException("Missing or invalid Authorization header in STOMP CONNECT");
    }

    String token = authHeader.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      throw new BadCredentialsException("Empty JWT token");
    }

    try {
      Jwt jwt = jwtDecoder.decode(token);
      JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
      accessor.setUser(authentication);
    } catch (JwtException ex) {
      throw new BadCredentialsException("Invalid or expired JWT token", ex);
    }
  }

  private void validateSubscription(StompHeaderAccessor accessor) {
    Principal user = requireAuthenticated(accessor);
    String destination = accessor.getDestination();
    if (destination == null) {
      throw new AccessDeniedException("Subscription destination is required");
    }

    if (destination.startsWith("/user/") && !destination.startsWith("/user/queue/")) {
      String expectedPrefix = "/user/" + user.getName() + "/queue/";
      if (!destination.startsWith(expectedPrefix)) {
        throw new AccessDeniedException("Cannot subscribe to another user's private queue");
      }
    }
  }

  private Principal requireAuthenticated(StompHeaderAccessor accessor) {
    Principal user = accessor.getUser();
    if (user == null) {
      throw new BadCredentialsException("Unauthenticated STOMP session");
    }
    return user;
  }
}
