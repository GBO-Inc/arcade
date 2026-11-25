package com.arcade.backend.engine.tictactoe;

import java.util.List;

public record TicTacToeState(
    List<List<String>> board, String xPlayer, String oPlayer, String currentPlayer) {}
