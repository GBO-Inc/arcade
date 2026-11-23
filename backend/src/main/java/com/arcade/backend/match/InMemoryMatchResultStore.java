package com.arcade.backend.match;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

@Component
public class InMemoryMatchResultStore implements MatchResultStore {

  private final List<MatchFinalResult> results = new CopyOnWriteArrayList<>();

  @Override
  public void save(MatchFinalResult result) {
    results.add(result);
  }

  public List<MatchFinalResult> findAll() {
    return List.copyOf(results);
  }
}
