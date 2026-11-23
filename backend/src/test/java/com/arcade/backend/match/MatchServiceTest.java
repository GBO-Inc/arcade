package com.arcade.backend.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcade.backend.engine.dummy.DummyGameEngine;
import com.arcade.backend.engine.dummy.DummyMove;
import com.arcade.backend.engine.dummy.DummyState;
import com.arcade.backend.lobby.GameType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchServiceTest {

  private InMemoryMatchRepository repository;
  private GameEngineRegistry registry;
  private InMemoryMatchResultStore resultStore;
  private RecordingEventPublisher eventPublisher;
  private MatchService service;

  @BeforeEach
  void setUp() {
    repository = new InMemoryMatchRepository();
    registry = new GameEngineRegistry(Map.of(GameType.TIC_TAC_TOE, new DummyGameEngine()));
    resultStore = new InMemoryMatchResultStore();
    eventPublisher = new RecordingEventPublisher();
    service =
        new MatchService(
            repository,
            registry,
            resultStore,
            eventPublisher,
            Duration.ofSeconds(30),
            new FixedBooleanRandom(true));
  }

  @AfterEach
  void tearDown() {
    service.destroy();
  }

  @Test
  void createFromFullRoomInitializesMatchInWaitingState() {
    Match<DummyState, DummyMove> match =
        service.createFromFullRoom(10L, GameType.TIC_TAC_TOE, List.of("101", "202"));

    assertThat(match.getId()).isNotNull();
    assertThat(match.getRoomId()).isEqualTo(10L);
    assertThat(match.getGameType()).isEqualTo(GameType.TIC_TAC_TOE);
    assertThat(match.getStatus()).isEqualTo(MatchStatus.WAITING);
    assertThat(match.getPlayers()).containsExactly("101", "202");
    assertThat(match.getCurrentTurnPlayerId()).isEqualTo("101");
    assertThat(match.getState().scores()).containsEntry("101", 0).containsEntry("202", 0);
    assertThat(match.getMoves()).isEmpty();
  }

  @Test
  void startingPlayerIsChosenRandomly() {
    MatchService firstStarts =
        new MatchService(
            repository,
            registry,
            resultStore,
            eventPublisher,
            Duration.ofSeconds(30),
            new FixedBooleanRandom(true));
    MatchService secondStarts =
        new MatchService(
            repository,
            registry,
            resultStore,
            eventPublisher,
            Duration.ofSeconds(30),
            new FixedBooleanRandom(false));

    try {
      Match<DummyState, DummyMove> m1 =
          firstStarts.createFromFullRoom(1L, GameType.TIC_TAC_TOE, List.of("101", "202"));
      Match<DummyState, DummyMove> m2 =
          secondStarts.createFromFullRoom(2L, GameType.TIC_TAC_TOE, List.of("101", "202"));

      assertThat(m1.getCurrentTurnPlayerId()).isEqualTo("101");
      assertThat(m2.getCurrentTurnPlayerId()).isEqualTo("202");
    } finally {
      firstStarts.destroy();
      secondStarts.destroy();
    }
  }

  @Test
  void matchMovesToInProgressOnlyWhenBothPlayersConnect() {
    Match<DummyState, DummyMove> match =
        service.createFromFullRoom(1L, GameType.TIC_TAC_TOE, List.of("101", "202"));

    assertThat(service.connectPlayer(match.getId(), "101").status()).isEqualTo(MatchStatus.WAITING);
    assertThat(service.connectPlayer(match.getId(), "101").status()).isEqualTo(MatchStatus.WAITING);
    assertThat(service.connectPlayer(match.getId(), "202").status())
        .isEqualTo(MatchStatus.IN_PROGRESS);
  }

  @Test
  void turnsAlternateAndMovesAreAppendedInOrder() {
    Match<DummyState, DummyMove> match = startDummyMatch(1L, "101", "202");

    DummyMove move1 = new DummyMove("101");
    MatchStateResponse afterMove1 = service.submitMove(match.getId(), "101", move1);
    assertThat(afterMove1.currentTurnPlayerId()).isEqualTo("202");
    assertThat(afterMove1.moveCount()).isEqualTo(1);

    DummyMove move2 = new DummyMove("202");
    MatchStateResponse afterMove2 = service.submitMove(match.getId(), "202", move2);
    assertThat(afterMove2.currentTurnPlayerId()).isEqualTo("101");
    assertThat(afterMove2.moveCount()).isEqualTo(2);

    assertThat(match.getMoves()).containsExactly(move1, move2);
    assertThat(match.getState().scores()).containsEntry("101", 1).containsEntry("202", 1);
  }

  @Test
  void moveByWrongPlayerOrBeforeInProgressIsRejected() {
    Match<DummyState, DummyMove> match =
        service.createFromFullRoom(1L, GameType.TIC_TAC_TOE, List.of("101", "202"));

    assertThatThrownBy(() -> service.submitMove(match.getId(), "101", new DummyMove("101")))
        .isInstanceOf(MatchException.class)
        .hasMessageContaining("not in progress");

    service.connectPlayer(match.getId(), "101");
    service.connectPlayer(match.getId(), "202");

    assertThatThrownBy(() -> service.submitMove(match.getId(), "202", new DummyMove("202")))
        .isInstanceOf(MatchException.class)
        .hasMessageContaining("Not your turn");

    assertThat(match.getMoves()).isEmpty();
  }

  @Test
  void abandoningTheRoomsWaitingMatchStopsItAndHidesItFromTheRoom() {
    Match<DummyState, DummyMove> match =
        service.createFromFullRoom(1L, GameType.TIC_TAC_TOE, List.of("101", "202"));
    service.connectPlayer(match.getId(), "101");

    service.abandonWaitingMatch(1L);

    assertThat(match.getStatus()).isEqualTo(MatchStatus.ABANDONED);
    assertThat(repository.findByRoomId(1L)).isEmpty();
    assertThat(repository.count()).isZero();
    assertThatThrownBy(() -> service.connectPlayer(match.getId(), "202"))
        .isInstanceOf(MatchException.class)
        .hasMessageContaining("Match not found");
    assertThatThrownBy(() -> service.submitMove(match.getId(), "101", new DummyMove("101")))
        .isInstanceOf(MatchException.class)
        .hasMessageContaining("Match not found");
  }

  @Test
  void abandoningIsANoOpWhenTheRoomHasNoMatch() {
    service.abandonWaitingMatch(99L);

    assertThat(repository.findByRoomId(99L)).isEmpty();
  }

  @Test
  void finishedMatchPublishesOnceSavesResultRemovesFromMemoryAndRejectsFurtherMoves() {
    Match<DummyState, DummyMove> match = startDummyMatch(1L, "101", "202");
    assertThat(repository.count()).isEqualTo(1);

    service.submitMove(match.getId(), "101", new DummyMove("101"));
    service.submitMove(match.getId(), "202", new DummyMove("202"));
    service.submitMove(match.getId(), "101", new DummyMove("101"));
    service.submitMove(match.getId(), "202", new DummyMove("202"));
    MatchStateResponse finalResponse =
        service.submitMove(match.getId(), "101", new DummyMove("101"));

    assertThat(finalResponse.status()).isEqualTo(MatchStatus.FINISHED);
    assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);

    assertThat(resultStore.findAll()).hasSize(1);
    assertThat(eventPublisher.published).hasSize(1);
    MatchFinalResult published = eventPublisher.published.get(0);
    assertThat(published.outcome()).isEqualTo(MatchFinalResult.Outcome.WIN);
    assertThat(published.winnerId()).isEqualTo("101");
    assertThat(published.players()).containsExactly("101", "202");

    assertThat(repository.count()).isZero();

    assertThatThrownBy(() -> service.submitMove(match.getId(), "202", new DummyMove("202")))
        .isInstanceOf(MatchException.class);
  }

  @Test
  void nearSimultaneousMovesForSameMatchAreSerializedAndProduceValidState() throws Exception {
    Match<DummyState, DummyMove> match = startDummyMatch(1L, "101", "202");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch go = new CountDownLatch(1);

      Callable<Boolean> task101 =
          () -> {
            ready.countDown();
            go.await();
            service.submitMove(match.getId(), "101", new DummyMove("101"));
            return true;
          };
      Callable<Boolean> task202 =
          () -> {
            ready.countDown();
            go.await();
            try {
              service.submitMove(match.getId(), "202", new DummyMove("202"));
              return true;
            } catch (MatchException expected) {
              return false;
            }
          };

      Future<Boolean> f1 = pool.submit(task101);
      Future<Boolean> f2 = pool.submit(task202);
      ready.await();
      go.countDown();

      boolean firstSucceeded = f1.get(5, TimeUnit.SECONDS);
      boolean secondSucceeded = f2.get(5, TimeUnit.SECONDS);

      assertThat(firstSucceeded).isTrue();
      if (secondSucceeded) {
        assertThat(match.getMoves()).containsExactly(new DummyMove("101"), new DummyMove("202"));
        assertThat(match.getCurrentTurnPlayerId()).isEqualTo("101");
      } else {
        assertThat(match.getMoves()).containsExactly(new DummyMove("101"));
        assertThat(match.getCurrentTurnPlayerId()).isEqualTo("202");
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void theSameMoveSentTwiceAtOnceIsAppliedOnlyOnce() throws Exception {
    Match<DummyState, DummyMove> match = startDummyMatch(1L, "101", "202");
    int attempts = 8;

    ExecutorService pool = Executors.newFixedThreadPool(attempts);
    try {
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < attempts; i++) {
        results.add(
            pool.submit(() -> {
              go.await();
              try {
                service.submitMove(match.getId(), "101", new DummyMove("101"));
                return true;
              } catch (MatchException rejected) {
                return false;
              }
            }));
      }
      go.countDown();

      int applied = 0;
      for (Future<Boolean> result : results) {
        if (result.get(5, TimeUnit.SECONDS)) {
          applied++;
        }
      }

      assertThat(applied).isEqualTo(1);
      assertThat(match.getMoves()).containsExactly(new DummyMove("101"));
      assertThat(match.getState().scores()).containsEntry("101", 1);
      assertThat(match.getCurrentTurnPlayerId()).isEqualTo("202");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void twoParallelMatchesRunToCompletionWithoutAffectingEachOther() throws Exception {
    Match<DummyState, DummyMove> m1 = startDummyMatch(1L, "101", "202");
    Match<DummyState, DummyMove> m2 = startDummyMatch(2L, "303", "404");
    assertThat(repository.count()).isEqualTo(2);

    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> f1 = pool.submit(() -> playToWin(m1.getId(), "101", "202"));
      Future<?> f2 = pool.submit(() -> playToWin(m2.getId(), "303", "404"));
      f1.get(5, TimeUnit.SECONDS);
      f2.get(5, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    assertThat(m1.getStatus()).isEqualTo(MatchStatus.FINISHED);
    assertThat(m2.getStatus()).isEqualTo(MatchStatus.FINISHED);
    assertThat(repository.count()).isZero();
    assertThat(resultStore.findAll()).hasSize(2);
    assertThat(eventPublisher.published)
        .extracting(MatchFinalResult::winnerId)
        .containsExactlyInAnyOrder("101", "303");
  }

  @Test
  void matchIsClosedAsAbandonedAfterTimeoutWhenBothPlayersDisconnect() throws Exception {
    service.destroy();
    service =
        new MatchService(
            repository,
            registry,
            resultStore,
            eventPublisher,
            Duration.ofMillis(80),
            new FixedBooleanRandom(true));

    Match<DummyState, DummyMove> match = startDummyMatch(1L, "101", "202");

    service.disconnectPlayer(match.getId(), "101");
    Thread.sleep(150);
    assertThat(match.getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);
    assertThat(repository.count()).isEqualTo(1);

    service.disconnectPlayer(match.getId(), "202");
    Thread.sleep(200);

    assertThat(match.getStatus()).isEqualTo(MatchStatus.ABANDONED);
    assertThat(repository.count()).isZero();
    assertThat(eventPublisher.published).hasSize(1);
    assertThat(eventPublisher.published.get(0).outcome())
        .isEqualTo(MatchFinalResult.Outcome.ABANDONED);
    assertThat(eventPublisher.published.get(0).winnerId()).isNull();
  }

  private void playToWin(UUID matchId, String p1, String p2) {
    service.submitMove(matchId, p1, new DummyMove(p1));
    service.submitMove(matchId, p2, new DummyMove(p2));
    service.submitMove(matchId, p1, new DummyMove(p1));
    service.submitMove(matchId, p2, new DummyMove(p2));
    service.submitMove(matchId, p1, new DummyMove(p1));
  }

  private Match<DummyState, DummyMove> startDummyMatch(long roomId, String p1, String p2) {
    Match<DummyState, DummyMove> match =
        service.createFromFullRoom(roomId, GameType.TIC_TAC_TOE, List.of(p1, p2));
    service.connectPlayer(match.getId(), p1);
    service.connectPlayer(match.getId(), p2);
    return match;
  }

  private static final class RecordingEventPublisher implements MatchEventPublisher {
    private final List<MatchFinalResult> published = new CopyOnWriteArrayList<>();

    @Override
    public void publishMatchEnded(MatchFinalResult result) {
      published.add(result);
    }
  }

  private static final class FixedBooleanRandom extends Random {
    private final boolean value;

    FixedBooleanRandom(boolean value) {
      this.value = value;
    }

    @Override
    public boolean nextBoolean() {
      return value;
    }
  }
}
