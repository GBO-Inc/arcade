package com.arcade.backend.match;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryMatchRepository implements MatchRepository {

  private final Map<UUID, Match<?, ?>> byId = new ConcurrentHashMap<>();

  @Override
  public Match<?, ?> save(Match<?, ?> match) {
    byId.put(match.getId(), match);
    return match;
  }

  @Override
  public Optional<Match<?, ?>> findById(UUID id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Optional<Match<?, ?>> findByRoomId(long roomId) {
    return byId.values().stream().filter(m -> m.getRoomId() == roomId).findFirst();
  }
}
