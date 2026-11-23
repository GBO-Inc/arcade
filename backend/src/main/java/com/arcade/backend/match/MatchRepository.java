package com.arcade.backend.match;

import java.util.Optional;
import java.util.UUID;

public interface MatchRepository {

  Match<?, ?> save(Match<?, ?> match);

  Optional<Match<?, ?>> findById(UUID id);

  /**
   * The room's current match; abandoned matches are skipped, since a refilled room gets a new one.
   */
  Optional<Match<?, ?>> findByRoomId(long roomId);
}
