package dev.olekshyr.api.blog;

import java.util.Set;
import java.util.TreeSet;

public class PublishRequirementsNotMetException extends RuntimeException {

  private final transient Set<String> missingFields;

  public PublishRequirementsNotMetException(Set<String> missingFields) {
    super("Cannot publish, missing fields: " + new TreeSet<>(missingFields));
    this.missingFields = Set.copyOf(missingFields);
  }

  public Set<String> getMissingFields() {
    return missingFields;
  }
}
