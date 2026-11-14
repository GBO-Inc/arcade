package com.arcade.backend.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arcade.backend.auth.LoginRequest;
import com.arcade.backend.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomIntegrationTest {

  private static final String PASSWORD = "correct horse";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @MockitoSpyBean private RoomCodeGenerator codeGenerator;

  @AfterEach
  void deleteData() {
    jdbcTemplate.update("delete from room_members");
    jdbcTemplate.update("delete from rooms");
    jdbcTemplate.update("delete from users");
  }

  @Test
  void createReturnsIdAndSixCharacterCodeWithoutLookAlikes() throws Exception {
    String alice = signUp("Alice");

    createRoom(alice, "CONNECT_FOUR")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(
            jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("[A-HJ-NP-Z2-9]{6}")))
        .andExpect(jsonPath("$.gameType").value("CONNECT_FOUR"))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.creatorName").value("Alice"))
        .andExpect(jsonPath("$.capacity").value(2))
        .andExpect(jsonPath("$.freeSeats").value(1));
  }

  @Test
  void createWithoutTokenOrWithUnknownGameIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gameType\":\"TIC_TAC_TOE\"}"))
        .andExpect(status().isUnauthorized());

    createRoom(signUp("Alice"), "POKER").andExpect(status().isBadRequest());
    postJson("/api/rooms", signUp("Bob"), "{}").andExpect(status().isBadRequest());
  }

  @Test
  void listShowsOpenRoomsWithGameCreatorAndFreeSeatsAndHidesFullOnes() throws Exception {
    String alice = signUp("Alice");
    String bob = signUp("Bob");
    String carol = signUp("Carol");
    String dave = signUp("Dave");
    createRoom(alice, "TIC_TAC_TOE").andExpect(status().isCreated());
    String bobsCode = codeOf(createRoom(bob, "CHECKERS"));
    joinRoom(carol, bobsCode).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, dave))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].gameType").value("TIC_TAC_TOE"))
        .andExpect(jsonPath("$[0].creatorName").value("Alice"))
        .andExpect(jsonPath("$[0].freeSeats").value(1));
  }

  @Test
  void joinByCodeFillsTheRoomAndIsIdempotentForTheSameUser() throws Exception {
    String alice = signUp("Alice");
    String bob = signUp("Bob");
    String code = codeOf(createRoom(alice, "TIC_TAC_TOE"));

    joinRoom(bob, code)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FULL"))
        .andExpect(jsonPath("$.freeSeats").value(0));
    joinRoom(bob, code.toLowerCase())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FULL"))
        .andExpect(jsonPath("$.freeSeats").value(0));
    joinRoom(alice, code).andExpect(status().isOk());

    assertThat(memberCount()).isEqualTo(2);
  }

  @Test
  void joinWithUnknownCodeReturnsNotFound() throws Exception {
    joinRoom(signUp("Alice"), "ZZZZZZ")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  void joinFullRoomReturnsConflict() throws Exception {
    String code = codeOf(createRoom(signUp("Alice"), "TIC_TAC_TOE"));
    joinRoom(signUp("Bob"), code).andExpect(status().isOk());

    joinRoom(signUp("Carol"), code)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Room is full"));
  }

  @Test
  void aUserCanBeInOnlyOneRoomAtATime() throws Exception {
    String alice = signUp("Alice");
    String bob = signUp("Bob");
    String otherCode = codeOf(createRoom(bob, "CHECKERS"));
    createRoom(alice, "TIC_TAC_TOE").andExpect(status().isCreated());

    createRoom(alice, "CONNECT_FOUR")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("You are already in another room"));
    joinRoom(alice, otherCode).andExpect(status().isConflict());
  }

  @Test
  void leavingKeepsTheRoomForTheOthersAndClosesItWhenTheLastPlayerLeaves() throws Exception {
    String alice = signUp("Alice");
    String bob = signUp("Bob");
    ResultActions created = createRoom(alice, "TIC_TAC_TOE");
    long roomId = idOf(created);
    String code = codeOf(created);
    joinRoom(bob, code).andExpect(status().isOk());

    // the creator leaves a room that still has a player in it
    leaveRoom(alice, roomId).andExpect(status().isNoContent());
    assertThat(statusOf(roomId)).isEqualTo("OPEN");
    mockMvc
        .perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, alice))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].freeSeats").value(1));

    // leaving frees alice to create another room
    createRoom(alice, "CHECKERS").andExpect(status().isCreated());

    leaveRoom(bob, roomId).andExpect(status().isNoContent());
    assertThat(statusOf(roomId)).isEqualTo("CLOSED");
    joinRoom(signUp("Carol"), code).andExpect(status().isNotFound());
  }

  @Test
  void leaveByNonMemberOrOfUnknownRoomReturnsNotFound() throws Exception {
    String alice = signUp("Alice");
    long roomId = idOf(createRoom(alice, "TIC_TAC_TOE"));

    leaveRoom(signUp("Bob"), roomId).andExpect(status().isNotFound());
    leaveRoom(alice, roomId + 1000).andExpect(status().isNotFound());
  }

  @Test
  void fullRoomReopensWhenSomeoneLeaves() throws Exception {
    String alice = signUp("Alice");
    String bob = signUp("Bob");
    ResultActions created = createRoom(alice, "TIC_TAC_TOE");
    long roomId = idOf(created);
    joinRoom(bob, codeOf(created)).andExpect(status().isOk());
    assertThat(statusOf(roomId)).isEqualTo("FULL");

    leaveRoom(bob, roomId).andExpect(status().isNoContent());

    assertThat(statusOf(roomId)).isEqualTo("OPEN");
  }

  @Test
  void aCodeCollisionWithAnActiveRoomIsRetriedWithAnotherCode() throws Exception {
    String taken = codeOf(createRoom(signUp("Alice"), "TIC_TAC_TOE"));
    doReturn(taken).doCallRealMethod().when(codeGenerator).next();

    String code = codeOf(createRoom(signUp("Bob"), "CHECKERS"));

    assertThat(code).isNotEqualTo(taken);
  }

  @Test
  void twoPlayersRacingForTheLastSeatLeaveExactlyOneWinner() throws Exception {
    String code = codeOf(createRoom(signUp("Alice"), "TIC_TAC_TOE"));
    String bob = signUp("Bob");
    String carol = signUp("Carol");

    List<Integer> statuses =
        runInParallel(() -> joinStatus(bob, code), () -> joinStatus(carol, code));

    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(memberCount()).isEqualTo(2);
  }

  @Test
  void oneUserCreatingTwoRoomsAtOnceGetsExactlyOne() throws Exception {
    String alice = signUp("Alice");

    List<Integer> statuses =
        runInParallel(
            () -> createRoom(alice, "TIC_TAC_TOE").andReturn().getResponse().getStatus(),
            () -> createRoom(alice, "CHECKERS").andReturn().getResponse().getStatus());

    assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    assertThat(jdbcTemplate.queryForObject("select count(*) from rooms", Integer.class))
        .isEqualTo(1);
  }

  private int joinStatus(String token, String code) throws Exception {
    return joinRoom(token, code).andReturn().getResponse().getStatus();
  }

  @SafeVarargs
  private static List<Integer> runInParallel(Callable<Integer>... tasks) throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Integer>> futures =
          java.util.Arrays.stream(tasks)
              .map(task ->
                  executor.submit(() -> {
                    start.await();
                    return task.call();
                  }))
              .toList();
      start.countDown();
      List<Integer> results = new java.util.ArrayList<>();
      for (Future<Integer> future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      executor.shutdownNow();
    }
  }

  private int memberCount() {
    return jdbcTemplate.queryForObject("select count(*) from room_members", Integer.class);
  }

  private String statusOf(long roomId) {
    return jdbcTemplate.queryForObject(
        "select status from rooms where id = ?", String.class, roomId);
  }

  private ResultActions createRoom(String token, String gameType) throws Exception {
    return postJson("/api/rooms", token, "{\"gameType\":\"" + gameType + "\"}");
  }

  private ResultActions joinRoom(String token, String code) throws Exception {
    return postJson("/api/rooms/join", token, "{\"code\":\"" + code + "\"}");
  }

  private ResultActions leaveRoom(String token, long roomId) throws Exception {
    return mockMvc.perform(
        post("/api/rooms/" + roomId + "/leave").header(HttpHeaders.AUTHORIZATION, token));
  }

  private ResultActions postJson(String path, String token, String json) throws Exception {
    return mockMvc.perform(
        post(path)
            .header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private static String codeOf(ResultActions result) throws Exception {
    return bodyOf(result).get("code").asText();
  }

  private static long idOf(ResultActions result) throws Exception {
    return bodyOf(result).get("id").asLong();
  }

  private static com.fasterxml.jackson.databind.JsonNode bodyOf(ResultActions result)
      throws Exception {
    return new ObjectMapper().readTree(result.andReturn().getResponse().getContentAsString());
  }

  /** Registers the user and returns a ready-to-send Authorization header value. */
  private String signUp(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest(
                            username, username.toLowerCase() + "@example.com", PASSWORD))))
        .andExpect(status().isCreated());
    String body =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD))))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + objectMapper.readTree(body).get("accessToken").asText();
  }
}
