package com.arcade.backend.engine;

// Az applyMove visszatérési értéke
public record MoveResult<TState>(boolean successful, TState newState, String rejectionReason) {
    public static <T> MoveResult<T> success(T state) {
        return new MoveResult<>(true, state, null);
    }
    public static <T> MoveResult<T> rejected(String reason) {
        return new MoveResult<>(false, null, reason);
    }
}