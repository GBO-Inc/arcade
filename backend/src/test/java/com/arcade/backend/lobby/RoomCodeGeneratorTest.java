package com.arcade.backend.lobby;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

  @Test
  void alphabetHasNoLookAlikeCharacters() {
    assertThat(RoomCodeGenerator.ALPHABET).doesNotContain("0", "O", "1", "I");
    assertThat(RoomCodeGenerator.ALPHABET).isEqualTo(RoomCodeGenerator.ALPHABET.toUpperCase());
  }

  @Test
  void codesAreSixCharactersOfUppercaseLettersAndDigits() {
    RoomCodeGenerator generator = new RoomCodeGenerator(new Random(42));

    for (int i = 0; i < 1000; i++) {
      assertThat(generator.next()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
    }
  }

  @Test
  void sameSeedGivesSameCodes() {
    assertThat(new RoomCodeGenerator(new Random(7)).next())
        .isEqualTo(new RoomCodeGenerator(new Random(7)).next());
  }
}
