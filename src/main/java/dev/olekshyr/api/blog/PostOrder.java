package dev.olekshyr.api.blog;

import org.jspecify.annotations.Nullable;

/**
 * The public list's order, the {@code sort} parameter (D27). Ties on the date
 * are broken by slug in the same direction, so newest-first is the exact
 * reverse of oldest-first and no post is skipped or repeated across pages.
 */
enum PostOrder {
  NEWEST("publishedAt,desc"),
  OLDEST("publishedAt,asc");

  static final String DEFAULT_PARAM = "publishedAt,desc";

  private final String param;

  PostOrder(String param) {
    this.param = param;
  }

  String param() {
    return param;
  }

  /** Exact match; anything else is a 400 naming the parameter. */
  static @Nullable PostOrder fromParam(String value) {
    if (value.isBlank()) {
      return null;
    }
    for (PostOrder order : values()) {
      if (order.param.equals(value)) {
        return order;
      }
    }
    throw new IllegalArgumentException("Unsupported sort: " + value);
  }
}
