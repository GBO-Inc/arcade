package com.arcade.backend.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchStateMachineTest {

    @Test
    void testValidTransitions() {
        MatchStateMachine stateMachine = new MatchStateMachine();
        assertEquals(MatchStatus.WAITING, stateMachine.getCurrentState());

        assertDoesNotThrow(() -> stateMachine.transitionTo(MatchStatus.IN_PROGRESS));
        assertEquals(MatchStatus.IN_PROGRESS, stateMachine.getCurrentState());

        assertDoesNotThrow(() -> stateMachine.transitionTo(MatchStatus.FINISHED));
        assertEquals(MatchStatus.FINISHED, stateMachine.getCurrentState());
    }

    @Test
    void testInvalidTransitionsAreRejected() {
        MatchStateMachine stateMachine = new MatchStateMachine();
        
        // WAITING-ből nem mehet rögtön FINISHED-be
        Exception exception = assertThrows(IllegalStateException.class, 
            () -> stateMachine.transitionTo(MatchStatus.FINISHED));
        
        assertTrue(exception.getMessage().contains("Invalid state transition"));
    }
}