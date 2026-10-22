package dev.olekshyr.api.blog;

/** Post and tag slugs: lowercase words joined by single hyphens (D10). */
final class SlugFormat {

  static final String REGEX = "^[a-z0-9]+(-[a-z0-9]+)*$";

  private SlugFormat() {}
}
