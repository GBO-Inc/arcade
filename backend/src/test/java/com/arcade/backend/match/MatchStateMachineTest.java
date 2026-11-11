package com.arcade.backend.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MatchStateMachineTest {

  @Test
  void startsInWaitingAndTransitionsToInProgressThenFinished() {
    MatchStateMachine machine = new MatchStateMachine();
    assertThat(machine.getCurrentState()).isEqualTo(MatchStatus.WAITING);

    machine.transitionTo(MatchStatus.IN_PROGRESS);
    assertThat(machine.getCurrentState()).isEqualTo(MatchStatus.IN_PROGRESS);

    machine.transitionTo(MatchStatus.FINISHED);
    assertThat(machine.getCurrentState()).isEqualTo(MatchStatus.FINISHED);
  }

  @Test
  void canTransitionToAbandonedFromWaiting() {
    MatchStateMachine machine = new MatchStateMachine();
    machine.transitionTo(MatchStatus.ABANDONED);
    assertThat(machine.getCurrentState()).isEqualTo(MatchStatus.ABANDONED);
  }

  @Test
  void canTransitionToAbandonedFromInProgress() {
    MatchStateMachine machine = new MatchStateMachine();
    machine.transitionTo(MatchStatus.IN_PROGRESS);
    machine.transitionTo(MatchStatus.ABANDONED);
    assertThat(machine.getCurrentState()).isEqualTo(MatchStatus.ABANDONED);
  }

  @Test
  void rejectsInvalidTransitionsFromWaitingAndInProgress() {
    MatchStateMachine waitingMachine = new MatchStateMachine();
    assertThatThrownBy(() -> waitingMachine.transitionTo(MatchStatus.FINISHED))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> waitingMachine.transitionTo(MatchStatus.WAITING))
        .isInstanceOf(IllegalStateException.class);

    MatchStateMachine inProgressMachine = new MatchStateMachine();
    inProgressMachine.transitionTo(MatchStatus.IN_PROGRESS);
    assertThatThrownBy(() -> inProgressMachine.transitionTo(MatchStatus.WAITING))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> inProgressMachine.transitionTo(MatchStatus.IN_PROGRESS))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(MatchStatus.class)
  void rejectsAllTransitionsFromFinished(MatchStatus targetState) {
    MatchStateMachine machine = new MatchStateMachine();
    machine.transitionTo(MatchStatus.IN_PROGRESS);
    machine.transitionTo(MatchStatus.FINISHED);

    assertThatThrownBy(() -> machine.transitionTo(targetState))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @EnumSource(MatchStatus.class)
  void rejectsAllTransitionsFromAbandoned(MatchStatus targetState) {
    MatchStateMachine machine = new MatchStateMachine();
    machine.transitionTo(MatchStatus.ABANDONED);

    assertThatThrownBy(() -> machine.transitionTo(targetState))
        .isInstanceOf(IllegalStateException.class);
  }
}
