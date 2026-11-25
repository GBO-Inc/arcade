package com.arcade.backend.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arcade.backend.AbstractIntegrationTest;
import com.arcade.backend.auth.LoginRequest;
import com.arcade.backend.auth.RegisterRequest;
import com.arcade.backend.match.MatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WaitingRoomIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "correct horse";
  private static final String ALLOWED_ORIGIN = "http://localhost:5173";
  private static final String TIC_TAC_TOE = "{\"gameType\":\"TIC_TAC_TOE\"}";

  @LocalServerPort private int port;
  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @MockitoSpyBean private MatchService matchService;

  private WebSocketStompClient stompClient;

  @BeforeEach
  void setUp() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());
  }

  @AfterEach
  void deleteData() {
    stompClient.stop();
    jdbcTemplate.update("delete from room_members");
    jdbcTemplate.update("delete from rooms");
    jdbcTemplate.update("delete from users");
  }

  @Test
  void twoPlayersReadyUpAndBothReceiveTheMatchStartedEvent() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");
    JsonNode room = createAndJoin(alice, bob);
    long roomId = room.get("id").asLong();
    BlockingQueue<JsonNode> aliceEvents = subscribeToRoom(alice, roomId);
    BlockingQueue<JsonNode> bobEvents = subscribeToRoom(bob, roomId);

    setReady(alice, roomId, true).andExpect(status().isOk());

    for (BlockingQueue<JsonNode> events : List.of(aliceEvents, bobEvents)) {
      JsonNode updated = nextEvent(events, "ROOM_UPDATED");
      assertThat(seat(updated.get("room"), alice.id()).get("ready").asBoolean()).isTrue();
      assertThat(seat(updated.get("room"), bob.id()).get("ready").asBoolean()).isFalse();
    }

    setReady(bob, roomId, true).andExpect(status().isOk());

    String aliceMatch = nextEvent(aliceEvents, "MATCH_STARTED").get("matchId").asText();
    String bobMatch = nextEvent(bobEvents, "MATCH_STARTED").get("matchId").asText();
    assertThat(aliceMatch).isEqualTo(bobMatch);
    getMatch(alice, aliceMatch).andExpect(status().isOk());
    getMatch(bob, aliceMatch).andExpect(status().isOk());
    assertThat(statusOf(roomId)).isEqualTo("STARTED");
  }

  @Test
  void simultaneousReadyStartsExactlyOneMatch() throws Exception {
    for (int round = 0; round < 5; round++) {
      Player first = signUp("First" + round);
      Player second = signUp("Second" + round);
      long roomId = createAndJoin(first, second).get("id").asLong();
      BlockingQueue<JsonNode> events = subscribeToRoom(first, roomId);

      List<Integer> statuses =
          runInParallel(
              () -> setReady(first, roomId, true).andReturn().getResponse().getStatus(),
              () -> setReady(second, roomId, true).andReturn().getResponse().getStatus());

      assertThat(statuses).containsOnly(200);
      nextEvent(events, "MATCH_STARTED");
      verify(matchService, times(1)).createFromFullRoom(eq(roomId), any(), any());
      Thread.sleep(300);
      assertThat(events)
          .as("no second start event")
          .noneMatch(event -> "MATCH_STARTED".equals(event.get("type").asText()));
      assertThat(statusOf(roomId)).isEqualTo("STARTED");
    }
  }

  @Test
  void leavingResetsTheOtherPlayersReadyState() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");
    Player carol = signUp("Carol");
    JsonNode room = createAndJoin(alice, bob);
    long roomId = room.get("id").asLong();
    BlockingQueue<JsonNode> aliceEvents = subscribeToRoom(alice, roomId);
    setReady(alice, roomId, true).andExpect(status().isOk());
    nextEvent(aliceEvents, "ROOM_UPDATED");

    leave(bob, roomId).andExpect(status().isNoContent());

    JsonNode updated = nextEvent(aliceEvents, "ROOM_UPDATED").get("room");
    assertThat(updated.get("seats")).hasSize(1);
    assertThat(updated.get("seats").get(0).get("ready").asBoolean()).isFalse();
    assertThat(updated.get("status").asText()).isEqualTo("OPEN");

    postJson("/api/rooms/join", carol.token(), "{\"code\":\"" + room.get("code").asText() + "\"}")
        .andExpect(status().isOk());
    getRoom(alice, roomId)
        .andExpect(jsonPath("$.seats[0].ready").value(false))
        .andExpect(jsonPath("$.seats[1].ready").value(false));
  }

  @Test
  void aPlayerCannotReadyUpAloneOrOutsideTheRoom() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");
    long roomId = idOf(createRoom(alice));

    setReady(alice, roomId, true).andExpect(status().isConflict());
    setReady(bob, roomId, true).andExpect(status().isNotFound());
    postJson("/api/rooms/" + roomId + "/ready", alice.token(), "{}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void onlyMembersCanReadTheRoom() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");
    long roomId = idOf(createRoom(alice));

    getRoom(alice, roomId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.creatorId").value(alice.id()))
        .andExpect(jsonPath("$.seats[0].displayName").value("Alice"))
        .andExpect(jsonPath("$.seats[0].ready").value(false));
    getRoom(bob, roomId).andExpect(status().isForbidden());
    getRoom(alice, roomId + 1000).andExpect(status().isNotFound());
  }

  @Test
  void onlyTheCreatorCanCloseTheRoom() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");
    long roomId = createAndJoin(alice, bob).get("id").asLong();

    close(bob, roomId).andExpect(status().isForbidden());

    assertThat(statusOf(roomId)).isEqualTo("FULL");
  }

  @Test
  void closingTheRoomNotifiesTheOtherPlayerAndFreesBoth() throws Exception {
    Player alice = signUp("Alice");
    Player bob = signUp("Bob");
    long roomId = createAndJoin(alice, bob).get("id").asLong();
    BlockingQueue<JsonNode> bobEvents = subscribeToRoom(bob, roomId);

    close(alice, roomId).andExpect(status().isNoContent());

    nextEvent(bobEvents, "ROOM_CLOSED");
    assertThat(statusOf(roomId)).isEqualTo("CLOSED");
    getRoom(bob, roomId).andExpect(status().isNotFound());
    postJson("/api/rooms", bob.token(), TIC_TAC_TOE).andExpect(status().isCreated());
  }

  private JsonNode createAndJoin(Player creator, Player joiner) throws Exception {
    JsonNode room = bodyOf(createRoom(creator).andExpect(status().isCreated()));
    postJson("/api/rooms/join", joiner.token(), "{\"code\":\"" + room.get("code").asText() + "\"}")
        .andExpect(status().isOk());
    return room;
  }

  private BlockingQueue<JsonNode> subscribeToRoom(Player player, long roomId) throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", player.token());
    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    StompSession session =
        stompClient
            .connectAsync(
                "ws://localhost:" + port + "/ws",
                handshakeHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
    session.subscribe(
        "/topic/rooms/" + roomId,
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return JsonNode.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            events.offer((JsonNode) payload);
          }
        });
    Thread.sleep(200);
    return events;
  }

  /** The next event of the given type; other event types before it are skipped. */
  private static JsonNode nextEvent(BlockingQueue<JsonNode> events, String type)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      JsonNode event = events.poll(100, TimeUnit.MILLISECONDS);
      if (event != null && type.equals(event.get("type").asText())) {
        return event;
      }
    }
    throw new AssertionError("No " + type + " event received");
  }

  private static JsonNode seat(JsonNode room, long userId) {
    for (JsonNode seat : room.get("seats")) {
      if (seat.get("userId").asLong() == userId) {
        return seat;
      }
    }
    throw new AssertionError("No seat for user " + userId);
  }

  @SafeVarargs
  private static List<Integer> runInParallel(Callable<Integer>... tasks) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Integer>> futures = new ArrayList<>();
      for (Callable<Integer> task : tasks) {
        futures.add(
            executor.submit(() -> {
              start.await();
              return task.call();
            }));
      }
      start.countDown();
      List<Integer> results = new ArrayList<>();
      for (Future<Integer> future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private String statusOf(long roomId) {
    return jdbcTemplate.queryForObject(
        "select status from rooms where id = ?", String.class, roomId);
  }

  private ResultActions createRoom(Player player) throws Exception {
    return postJson("/api/rooms", player.token(), TIC_TAC_TOE);
  }

  private ResultActions setReady(Player player, long roomId, boolean ready) throws Exception {
    return postJson("/api/rooms/" + roomId + "/ready", player.token(), "{\"ready\":" + ready + "}");
  }

  private ResultActions leave(Player player, long roomId) throws Exception {
    return mockMvc.perform(
        post("/api/rooms/" + roomId + "/leave").header(HttpHeaders.AUTHORIZATION, player.token()));
  }

  private ResultActions close(Player player, long roomId) throws Exception {
    return mockMvc.perform(
        post("/api/rooms/" + roomId + "/close").header(HttpHeaders.AUTHORIZATION, player.token()));
  }

  private ResultActions getRoom(Player player, long roomId) throws Exception {
    return mockMvc.perform(
        get("/api/rooms/" + roomId).header(HttpHeaders.AUTHORIZATION, player.token()));
  }

  private ResultActions getMatch(Player player, String matchId) throws Exception {
    return mockMvc.perform(
        get("/api/matches/" + matchId).header(HttpHeaders.AUTHORIZATION, player.token()));
  }

  private ResultActions postJson(String path, String token, String json) throws Exception {
    return mockMvc.perform(
        post(path)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private JsonNode bodyOf(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private long idOf(ResultActions result) throws Exception {
    return bodyOf(result).get("id").asLong();
  }

  private record Player(long id, String token) {}

  private Player signUp(String username) throws Exception {
    long id =
        bodyOf(
                mockMvc
                    .perform(
                        post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    new RegisterRequest(
                                        username,
                                        username.toLowerCase() + "@example.com",
                                        PASSWORD))))
                    .andExpect(status().isCreated()))
            .get("id")
            .asLong();
    JsonNode login =
        bodyOf(
            mockMvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD)))));
    return new Player(id, "Bearer " + login.get("accessToken").asText());
  }
}
