package com.arcade.backend.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.arcade.backend.engine.dummy.DummyGameEngine;
import com.arcade.backend.engine.dummy.DummyMove;
import com.arcade.backend.engine.dummy.DummyState;
import com.arcade.backend.lobby.GameType;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchServiceTest {

  private InMemoryMatchRepository repository;
  private GameEngineRegistry registry;

  @BeforeEach
  void setUp() {
    repository = new InMemoryMatchRepository();
    registry = new GameEngineRegistry(Map.of(GameType.TIC_TAC_TOE, new DummyGameEngine()));
  }

  @Test
  void createFromFullRoomInitializesMatchInWaitingState() {
    MatchService service = new MatchService(repository, registry, new FixedBooleanRandom(true));

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
    MatchService firstStarts = new MatchService(repository, registry, new FixedBooleanRandom(true));
    MatchService secondStarts =
        new MatchService(repository, registry, new FixedBooleanRandom(false));

    Match<DummyState, DummyMove> m1 =
        firstStarts.createFromFullRoom(1L, GameType.TIC_TAC_TOE, List.of("101", "202"));
    Match<DummyState, DummyMove> m2 =
        secondStarts.createFromFullRoom(2L, GameType.TIC_TAC_TOE, List.of("101", "202"));

    assertThat(m1.getCurrentTurnPlayerId()).isEqualTo("101");
    assertThat(m2.getCurrentTurnPlayerId()).isEqualTo("202");
  }

  @Test
  void matchMovesToInProgressOnlyWhenBothPlayersConnect() {
    MatchService service = new MatchService(repository, registry, new FixedBooleanRandom(true));
    Match<DummyState, DummyMove> match =
        service.createFromFullRoom(1L, GameType.TIC_TAC_TOE, List.of("101", "202"));

    MatchStateResponse afterFirst = service.connectPlayer(match.getId(), "101");
    assertThat(afterFirst.status()).isEqualTo(MatchStatus.WAITING);

    MatchStateResponse afterDuplicate = service.connectPlayer(match.getId(), "101");
    assertThat(afterDuplicate.status()).isEqualTo(MatchStatus.WAITING);

    MatchStateResponse afterSecond = service.connectPlayer(match.getId(), "202");
    assertThat(afterSecond.status()).isEqualTo(MatchStatus.IN_PROGRESS);
  }

  @Test
  void turnsAlternateAndMovesAreAppendedInOrder() {
    MatchService service = new MatchService(repository, registry, new FixedBooleanRandom(true));
    Match<DummyState, DummyMove> match =
        service.createFromFullRoom(1L, GameType.TIC_TAC_TOE, List.of("101", "202"));
    service.connectPlayer(match.getId(), "101");
    service.connectPlayer(match.getId(), "202");

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
    MatchService service = new MatchService(repository, registry, new FixedBooleanRandom(true));
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
