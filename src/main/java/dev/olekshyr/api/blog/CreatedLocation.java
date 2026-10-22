package dev.olekshyr.api.blog;

import java.net.URI;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** The Location of a resource a POST just created: the request URI plus its id. */
final class CreatedLocation {

  private CreatedLocation() {}

  static URI of(long id) {
    return ServletUriComponentsBuilder
      .fromCurrentRequestUri()
      .path("/{id}")
      .buildAndExpand(id)
      .toUri();
  }
}
