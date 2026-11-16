package dev.olekshyr.api.blog;

import org.jspecify.annotations.Nullable;

/**
 * The {@code include} parameter of the public post and tag lists (D28).
 * Absent means only posts visible in the resolved locale.
 */
enum ListInclude {
  /** Every post visible in at least one locale. */
  UNTRANSLATED("untranslated");

  private final String param;

  ListInclude(String param) {
    this.param = param;
  }

  /** Exact match; blank counts as absent, anything else is a 400. */
  static @Nullable ListInclude fromParam(String value) {
    if (value.isBlank()) {
      return null;
    }
    for (ListInclude include : values()) {
      if (include.param.equals(value)) {
        return include;
      }
    }
    throw new IllegalArgumentException("Unsupported include: " + value);
  }
}
