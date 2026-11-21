package com.arcade.backend.profile;

import java.util.List;

/**
 * The fixed set of avatar colours. The frontend keeps the same list in
 * `frontend/src/api/profile.ts`.
 */
public final class AvatarPalette {

  public static final List<String> COLOURS =
      List.of(
          "#EF4444", "#F97316", "#EAB308", "#22C55E", "#14B8A6", "#3B82F6", "#8B5CF6", "#EC4899");

  private AvatarPalette() {}
}
