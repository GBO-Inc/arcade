package com.arcade.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcade.backend.config.WebSocketEchoController.EchoMessage;
import com.arcade.backend.config.WebSocketEchoController.EchoResponse;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketStompIntegrationTest {

  @LocalServerPort private int port;

  @Autowired private JwtEncoder jwtEncoder;

  private WebSocketStompClient stompClient;
  private String wsUrl;

  @BeforeEach
  void setUp() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    wsUrl = "ws://localhost:" + port + "/ws";
  }

  private String generateToken(String subject, Instant expiresAt) {
    Instant issuedAt = expiresAt.minusSeconds(300);
    JwtClaimsSet claims =
        JwtClaimsSet.builder().subject(subject).issuedAt(issuedAt).expiresAt(expiresAt).build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private StompSession connectWithToken(String token, CompletableFuture<Throwable> errorFuture)
      throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    if (token != null) {
      connectHeaders.add("Authorization", "Bearer " + token);
    }

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin("http://localhost:5173");

    return stompClient
        .connectAsync(
            wsUrl,
            handshakeHeaders,
            connectHeaders,
            new StompSessionHandlerAdapter() {
              @Override
              public void handleFrame(StompHeaders headers, Object payload) {
                errorFuture.complete(new RuntimeException(headers.getFirst("message")));
              }

              @Override
              public void handleException(
                  StompSession session,
                  StompCommand command,
                  StompHeaders headers,
                  byte[] payload,
                  Throwable exception) {
                errorFuture.complete(exception);
              }

              @Override
              public void handleTransportError(StompSession session, Throwable exception) {
                errorFuture.complete(exception);
              }
            })
        .get(5, TimeUnit.SECONDS);
  }

  private StompFrameHandler createHandler(BlockingQueue<EchoResponse> queue) {
    return new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return EchoResponse.class;
      }

      @Override
      public void handleFrame(StompHeaders headers, Object payload) {
        queue.offer((EchoResponse) payload);
      }
    };
  }

  @Test
  void echoMessageRoundTripsBetweenTwoConnectedClients() throws Exception {
    String tokenPlayer1 = generateToken("player-1", Instant.now().plusSeconds(3600));
    String tokenPlayer2 = generateToken("player-2", Instant.now().plusSeconds(3600));

    CompletableFuture<Throwable> err1 = new CompletableFuture<>();
    CompletableFuture<Throwable> err2 = new CompletableFuture<>();

    StompSession session1 = connectWithToken(tokenPlayer1, err1);
    StompSession session2 = connectWithToken(tokenPlayer2, err2);

    BlockingQueue<EchoResponse> topicQueueClient1 = new LinkedBlockingQueue<>();
    BlockingQueue<EchoResponse> topicQueueClient2 = new LinkedBlockingQueue<>();
    BlockingQueue<EchoResponse> privateQueueClient1 = new LinkedBlockingQueue<>();
    BlockingQueue<EchoResponse> privateQueueClient2 = new LinkedBlockingQueue<>();

    session1.subscribe("/topic/echo", createHandler(topicQueueClient1));
    session2.subscribe("/topic/echo", createHandler(topicQueueClient2));
    session1.subscribe("/user/queue/echo", createHandler(privateQueueClient1));
    session2.subscribe("/user/queue/echo", createHandler(privateQueueClient2));

    Thread.sleep(150);

    session1.send("/app/echo", new EchoMessage("hello-arena"));

    EchoResponse receivedBy1 = topicQueueClient1.poll(5, TimeUnit.SECONDS);
    EchoResponse receivedBy2 = topicQueueClient2.poll(5, TimeUnit.SECONDS);

    assertThat(receivedBy1).isEqualTo(new EchoResponse("player-1", "hello-arena"));
    assertThat(receivedBy2).isEqualTo(new EchoResponse("player-1", "hello-arena"));

    session2.send("/app/echo/private", new EchoMessage("secret-move"));

    EchoResponse privateReceivedBy2 = privateQueueClient2.poll(5, TimeUnit.SECONDS);
    EchoResponse privateReceivedBy1 = privateQueueClient1.poll(300, TimeUnit.MILLISECONDS);

    assertThat(privateReceivedBy2).isEqualTo(new EchoResponse("player-2", "secret-move"));
    assertThat(privateReceivedBy1).isNull();

    session1.disconnect();
    session2.disconnect();
  }

  @Test
  void connectFailsWhenTokenIsMissingInvalidOrExpired() {
    String expiredToken = generateToken("player-1", Instant.now().minusSeconds(60));

    for (String badToken : List.of("", "invalid.jwt.token", expiredToken)) {
      CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
      assertThatThrownBy(() -> connectWithToken(badToken, errorFuture))
          .isInstanceOfAny(ExecutionException.class, Exception.class);
    }
  }

  @Test
  void clientCannotSubscribeToAnotherUsersPrivateQueue() throws Exception {
    String tokenPlayer1 = generateToken("player-1", Instant.now().plusSeconds(3600));
    CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();

    StompSession session1 = connectWithToken(tokenPlayer1, errorFuture);

    session1.subscribe(
        "/user/player-2/queue/echo",
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return EchoResponse.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {}
        });

    Throwable error = errorFuture.get(5, TimeUnit.SECONDS);
    assertThat(error).isNotNull();
    if (session1.isConnected()) {
      session1.disconnect();
    }
  }
}
