package com.arcade.backend.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcade.backend.AbstractIntegrationTest;
import com.arcade.backend.engine.dummy.DummyMove;
import com.arcade.backend.lobby.GameType;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchMovePipelineIT extends AbstractIntegrationTest {

  private static final String ALLOWED_ORIGIN = "http://localhost:5173";

  @LocalServerPort private int port;

  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private MatchService matchService;

  private WebSocketStompClient stompClient;
  private String wsUrl;

  @BeforeEach
  void setUp() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    wsUrl = "ws://localhost:" + port + "/ws";
  }

  private String validToken(String subject) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private StompSession connectAs(String subject) throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer " + validToken(subject));
    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);

    return stompClient
        .connectAsync(wsUrl, handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {})
        .get(5, TimeUnit.SECONDS);
  }

  private <T> StompFrameHandler handler(Class<T> type, BlockingQueue<T> queue) {
    return new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return type;
      }

      @Override
      @SuppressWarnings("unchecked")
      public void handleFrame(StompHeaders headers, Object payload) {
        queue.offer((T) payload);
      }
    };
  }

  @Test
  void ticTacToeMoveIsAppliedAndOccupiedCellIsRejected() throws Exception {
    Match<?, ?> match =
        matchService.createFromFullRoom(102L, GameType.TIC_TAC_TOE, List.of("t1", "t2"));
    UUID matchId = match.getId();
    String xPlayer = match.getPlayers().get(0);
    String oPlayer = match.getPlayers().get(1);
    matchService.connectPlayer(matchId, xPlayer);
    matchService.connectPlayer(matchId, oPlayer);

    StompSession xSession = connectAs(xPlayer);
    StompSession oSession = connectAs(oPlayer);
    BlockingQueue<JsonNode> oTopic = new LinkedBlockingQueue<>();
    BlockingQueue<MatchErrorMessage> xErrors = new LinkedBlockingQueue<>();
    BlockingQueue<MatchErrorMessage> oErrors = new LinkedBlockingQueue<>();
    String moveDest = "/app/match/" + matchId + "/move";
    oSession.subscribe("/topic/matches/" + matchId, handler(JsonNode.class, oTopic));
    xSession.subscribe("/user/queue/errors", handler(MatchErrorMessage.class, xErrors));
    oSession.subscribe("/user/queue/errors", handler(MatchErrorMessage.class, oErrors));

    Thread.sleep(200);
    oTopic.clear();

    xSession.send(moveDest, Map.of("row", 1, "col", 1));
    JsonNode applied = oTopic.poll(5, TimeUnit.SECONDS);
    assertThat(applied).isNotNull();
    assertThat(applied.get("move").get("row").asInt()).isEqualTo(1);
    assertThat(applied.get("match").get("state").get("board").get(1).get(1).asText())
        .isEqualTo("X");
    assertThat(applied.get("match").get("currentTurnPlayerId").asText()).isEqualTo(oPlayer);

    oSession.send(moveDest, Map.of("row", 1, "col", 1));
    MatchErrorMessage occupied = oErrors.poll(5, TimeUnit.SECONDS);
    assertThat(occupied).isNotNull();
    assertThat(occupied.code()).isEqualTo("INVALID_MOVE");
    assertThat(occupied.matchId()).isEqualTo(matchId);
    assertThat(xErrors.poll(200, TimeUnit.MILLISECONDS)).isNull();
    assertThat(oTopic.poll(200, TimeUnit.MILLISECONDS)).isNull();

    xSession.disconnect();
    oSession.disconnect();
  }

  @Test
  void playsDummyGameToCompletionAndRejectsAllInvalidCases() throws Exception {
    Match<?, ?> match =
        matchService.createFromFullRoom(101L, GameType.CONNECT_FOUR, List.of("p1", "p2"));
    UUID matchId = match.getId();
    String firstPlayer = match.getPlayers().get(0);
    String secondPlayer = match.getPlayers().get(1);

    matchService.connectPlayer(matchId, firstPlayer);
    matchService.connectPlayer(matchId, secondPlayer);

    StompSession firstSession = connectAs(firstPlayer);
    StompSession secondSession = connectAs(secondPlayer);
    StompSession outsiderSession = connectAs("outsider");

    BlockingQueue<JsonNode> firstTopic = new LinkedBlockingQueue<>();
    BlockingQueue<JsonNode> secondTopic = new LinkedBlockingQueue<>();
    BlockingQueue<MatchErrorMessage> firstErrors = new LinkedBlockingQueue<>();
    BlockingQueue<MatchErrorMessage> secondErrors = new LinkedBlockingQueue<>();
    BlockingQueue<MatchErrorMessage> outsiderErrors = new LinkedBlockingQueue<>();

    String topicDest = "/topic/matches/" + matchId;
    String moveDest = "/app/match/" + matchId + "/move";

    firstSession.subscribe(topicDest, handler(JsonNode.class, firstTopic));
    secondSession.subscribe(topicDest, handler(JsonNode.class, secondTopic));
    firstSession.subscribe("/user/queue/errors", handler(MatchErrorMessage.class, firstErrors));
    secondSession.subscribe("/user/queue/errors", handler(MatchErrorMessage.class, secondErrors));
    outsiderSession.subscribe(
        "/user/queue/errors", handler(MatchErrorMessage.class, outsiderErrors));

    Thread.sleep(200);
    firstTopic.clear();
    secondTopic.clear();

    // 1. Non-participant rejection
    outsiderSession.send(moveDest, new DummyMove("outsider"));
    MatchErrorMessage outsiderErr = outsiderErrors.poll(5, TimeUnit.SECONDS);
    assertThat(outsiderErr).isNotNull();
    assertThat(outsiderErr.code()).isEqualTo("NOT_PARTICIPANT");
    assertThat(outsiderErr.matchId()).isEqualTo(matchId);
    assertThat(outsiderErr.message()).isNotBlank();
    assertThat(firstErrors.poll(200, TimeUnit.MILLISECONDS)).isNull();
    assertThat(secondErrors.poll(200, TimeUnit.MILLISECONDS)).isNull();
    assertThat(firstTopic.poll(200, TimeUnit.MILLISECONDS)).isNull();

    // 2. Out-of-turn rejection
    secondSession.send(moveDest, new DummyMove(secondPlayer));
    MatchErrorMessage turnErr = secondErrors.poll(5, TimeUnit.SECONDS);
    assertThat(turnErr).isNotNull();
    assertThat(turnErr.code()).isEqualTo("NOT_YOUR_TURN");
    assertThat(turnErr.message()).isNotBlank();
    assertThat(firstErrors.poll(200, TimeUnit.MILLISECONDS)).isNull();
    assertThat(firstTopic.poll(200, TimeUnit.MILLISECONDS)).isNull();

    // 3. Illegal move rejection (firstPlayer is on turn, but sends wrong playerId in DummyMove)
    firstSession.send(moveDest, new DummyMove(secondPlayer));
    MatchErrorMessage illegalErr = firstErrors.poll(5, TimeUnit.SECONDS);
    assertThat(illegalErr).isNotNull();
    assertThat(illegalErr.code()).isEqualTo("INVALID_MOVE");
    assertThat(illegalErr.message()).isNotBlank();
    assertThat(secondErrors.poll(200, TimeUnit.MILLISECONDS)).isNull();
    assertThat(firstTopic.poll(200, TimeUnit.MILLISECONDS)).isNull();

    // 4. Play full dummy game to completion (5 moves: firstPlayer reaches score 3)
    String[] moveOrder = {firstPlayer, secondPlayer, firstPlayer, secondPlayer, firstPlayer};
    StompSession[] sessions = {
      firstSession, secondSession, firstSession, secondSession, firstSession
    };

    for (int i = 0; i < moveOrder.length; i++) {
      sessions[i].send(moveDest, new DummyMove(moveOrder[i]));

      JsonNode msg1 = firstTopic.poll(5, TimeUnit.SECONDS);
      JsonNode msg2 = secondTopic.poll(5, TimeUnit.SECONDS);
      assertThat(msg1).isNotNull();
      assertThat(msg2).isNotNull();
      assertThat(msg1.get("movedBy").asText()).isEqualTo(moveOrder[i]);
      assertThat(msg1.get("move").get("playerId").asText()).isEqualTo(moveOrder[i]);
      assertThat(msg1.get("match").get("moveCount").asInt()).isEqualTo(i + 1);
      assertThat(msg2.get("match").get("moveCount").asInt()).isEqualTo(i + 1);

      if (i == moveOrder.length - 1) {
        assertThat(msg1.get("match").get("status").asText()).isEqualTo("FINISHED");
      }
    }

    // Drain the MatchFinalResult broadcast on /topic/matches/{id}
    assertThat(firstTopic.poll(5, TimeUnit.SECONDS)).isNotNull();
    assertThat(secondTopic.poll(5, TimeUnit.SECONDS)).isNotNull();

    // 5. Move in a finished match rejection
    secondSession.send(moveDest, new DummyMove(secondPlayer));
    MatchErrorMessage finishedErr = secondErrors.poll(5, TimeUnit.SECONDS);
    assertThat(finishedErr).isNotNull();
    assertThat(finishedErr.code()).isEqualTo("MATCH_FINISHED");
    assertThat(finishedErr.message()).isNotBlank();
    assertThat(firstErrors.poll(200, TimeUnit.MILLISECONDS)).isNull();

    firstSession.disconnect();
    secondSession.disconnect();
    outsiderSession.disconnect();
  }
}
