package com.arcade.backend.match;

import java.util.Optional;
import java.util.UUID;

public interface MatchRepository {

  Match<?, ?> save(Match<?, ?> match);

  Optional<Match<?, ?>> findById(UUID id);

  Optional<Match<?, ?>> findByRoomId(long roomId);
}
