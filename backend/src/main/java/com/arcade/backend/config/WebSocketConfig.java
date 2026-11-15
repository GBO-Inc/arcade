package com.arcade.backend.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

/**
 * STOMP over WebSocket configuration.
 *
 * <p>Destinations convention:
 *
 * <ul>
 *   <li>{@code /app/**} - Client to server messages routed to {@code @MessageMapping} handlers.
 *   <li>{@code /user/queue/**} - Private user-specific messages (only own subscriptions allowed).
 *   <li>{@code /topic/**} - Broadcast messages to all subscribed clients.
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
  private final List<String> allowedOrigins;

  public WebSocketConfig(
      StompAuthChannelInterceptor stompAuthChannelInterceptor,
      @Value("${app.websocket.allowed-origins:http://localhost:5173}")
          List<String> allowedOrigins) {
    this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    this.allowedOrigins = allowedOrigins;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new));

    registry.setErrorHandler(
        new StompSubProtocolErrorHandler() {
          @Override
          public Message<byte[]> handleClientMessageProcessingError(
              Message<byte[]> clientMessage, Throwable ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
            accessor.setMessage(cause.getMessage());
            accessor.setLeaveMutable(true);
            return MessageBuilder.createMessage(
                cause.getMessage() != null
                    ? cause.getMessage().getBytes(StandardCharsets.UTF_8)
                    : new byte[0],
                accessor.getMessageHeaders());
          }
        });
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app");
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompAuthChannelInterceptor);
  }
}
