package com.arcade.backend.lobby;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Makes 6-character room codes that are easy to read out loud: no 0, O, 1 or I. */
@Component
public class RoomCodeGenerator {

  static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  static final int LENGTH = 6;

  private final RandomGenerator random;

  @Autowired
  public RoomCodeGenerator() {
    this(new SecureRandom());
  }

  RoomCodeGenerator(RandomGenerator random) {
    this.random = random;
  }

  public String next() {
    StringBuilder code = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return code.toString();
  }
}
