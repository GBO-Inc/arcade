package com.arcade.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcade.backend.config.WebSocketEchoController.EchoMessage;
import com.arcade.backend.config.WebSocketEchoController.EchoResponse;
import java.lang.reflect.Type;
import java.time.Instant;
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

  private static final String ALLOWED_ORIGIN = "http://localhost:5173";

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

  private String validToken(String subject) {
    return generateToken(subject, Instant.now().plusSeconds(3600));
  }

  // errorFuture receives the text of the server's ERROR frame, or the transport error if the
  // connection drops without one.
  private CompletableFuture<StompSession> connect(
      String authorizationHeader, String origin, CompletableFuture<String> errorFuture) {
    StompHeaders connectHeaders = new StompHeaders();
    if (authorizationHeader != null) {
      connectHeaders.add("Authorization", authorizationHeader);
    }

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(origin);

    return stompClient.connectAsync(
        wsUrl,
        handshakeHeaders,
        connectHeaders,
        new StompSessionHandlerAdapter() {
          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            errorFuture.complete(headers.getFirst("message"));
          }

          @Override
          public void handleException(
              StompSession session,
              StompCommand command,
              StompHeaders headers,
              byte[] payload,
              Throwable exception) {
            errorFuture.complete(exception.toString());
          }

          @Override
          public void handleTransportError(StompSession session, Throwable exception) {
            errorFuture.complete(exception.toString());
          }
        });
  }

  private StompSession connectAs(String subject, CompletableFuture<String> errorFuture)
      throws Exception {
    return connect("Bearer " + validToken(subject), ALLOWED_ORIGIN, errorFuture)
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

  private void assertConnectRejected(String authorizationHeader, String expectedError) {
    CompletableFuture<String> errorFuture = new CompletableFuture<>();

    assertThatThrownBy(() ->
            connect(authorizationHeader, ALLOWED_ORIGIN, errorFuture).get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
    assertThat(errorFuture).isCompletedWithValue(expectedError);
  }

  @Test
  void echoMessageRoundTripsBetweenTwoConnectedClients() throws Exception {
    StompSession session1 = connectAs("player-1", new CompletableFuture<>());
    StompSession session2 = connectAs("player-2", new CompletableFuture<>());

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

    assertThat(topicQueueClient1.poll(5, TimeUnit.SECONDS))
        .isEqualTo(new EchoResponse("player-1", "hello-arena"));
    assertThat(topicQueueClient2.poll(5, TimeUnit.SECONDS))
        .isEqualTo(new EchoResponse("player-1", "hello-arena"));

    session2.send("/app/echo/private", new EchoMessage("secret-move"));

    assertThat(privateQueueClient2.poll(5, TimeUnit.SECONDS))
        .isEqualTo(new EchoResponse("player-2", "secret-move"));
    assertThat(privateQueueClient1.poll(300, TimeUnit.MILLISECONDS)).isNull();

    session1.disconnect();
    session2.disconnect();
  }

  @Test
  void connectWithoutAuthorizationHeaderIsRejectedWithErrorFrame() {
    assertConnectRejected(null, "Missing or invalid Authorization header in STOMP CONNECT");
  }

  @Test
  void connectWithEmptyTokenIsRejectedWithErrorFrame() {
    assertConnectRejected("Bearer ", "Empty JWT token");
  }

  @Test
  void connectWithMalformedTokenIsRejectedWithErrorFrame() {
    assertConnectRejected("Bearer invalid.jwt.token", "Invalid or expired JWT token");
  }

  @Test
  void connectWithExpiredTokenIsRejectedWithErrorFrame() {
    String expiredToken = generateToken("player-1", Instant.now().minusSeconds(60));
    assertConnectRejected("Bearer " + expiredToken, "Invalid or expired JWT token");
  }

  @Test
  void handshakeFromOriginNotInAllowedListIsRejected() {
    CompletableFuture<String> errorFuture = new CompletableFuture<>();

    assertThatThrownBy(() ->
            connect("Bearer " + validToken("player-1"), "http://evil.example", errorFuture)
                .get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void clientCannotSubscribeToAnotherUsersPrivateQueue() throws Exception {
    CompletableFuture<String> errorFuture = new CompletableFuture<>();
    StompSession session1 = connectAs("player-1", errorFuture);

    session1.subscribe("/user/player-2/queue/echo", createHandler(new LinkedBlockingQueue<>()));

    assertThat(errorFuture.get(5, TimeUnit.SECONDS))
        .isEqualTo("Subscriptions are only allowed to /topic/** and /user/queue/**");
  }

  @Test
  void clientCannotSubscribeToRawBrokerQueue() throws Exception {
    CompletableFuture<String> errorFuture = new CompletableFuture<>();
    StompSession session1 = connectAs("player-1", errorFuture);

    session1.subscribe("/queue/echo", createHandler(new LinkedBlockingQueue<>()));

    assertThat(errorFuture.get(5, TimeUnit.SECONDS))
        .isEqualTo("Subscriptions are only allowed to /topic/** and /user/queue/**");
  }

  @Test
  void clientCannotSendDirectlyToBrokerTopic() throws Exception {
    StompSession listener = connectAs("player-2", new CompletableFuture<>());
    BlockingQueue<EchoResponse> received = new LinkedBlockingQueue<>();
    listener.subscribe("/topic/echo", createHandler(received));

    CompletableFuture<String> errorFuture = new CompletableFuture<>();
    StompSession spoofer = connectAs("player-1", errorFuture);
    Thread.sleep(150);

    spoofer.send("/topic/echo", new EchoResponse("player-2", "forged"));

    assertThat(errorFuture.get(5, TimeUnit.SECONDS))
        .isEqualTo("Messages can only be sent to /app/**");
    assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();

    listener.disconnect();
  }
}
