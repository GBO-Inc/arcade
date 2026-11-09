package com.arcade.backend.match;

public class MatchStateMachine {
    private MatchStatus currentState;

    public MatchStateMachine() {
        this.currentState = MatchStatus.WAITING;
    }

    public MatchStatus getCurrentState() {
        return currentState;
    }

    public void transitionTo(MatchStatus nextState) {
        if (!isValidTransition(this.currentState, nextState)) {
            throw new IllegalStateException("Invalid state transition from " + this.currentState + " to " + nextState);
        }
        this.currentState = nextState;
    }

    private boolean isValidTransition(MatchStatus current, MatchStatus next) {
        return switch (current) {
            case WAITING -> next == MatchStatus.IN_PROGRESS || next == MatchStatus.ABANDONED;
            case IN_PROGRESS -> next == MatchStatus.FINISHED || next == MatchStatus.ABANDONED;
            case FINISHED, ABANDONED -> false; // Terminális állapotok
        };
    }
}