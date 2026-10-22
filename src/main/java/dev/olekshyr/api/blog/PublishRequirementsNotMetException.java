package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.FieldErrorCode;
import dev.olekshyr.api.shared.FieldViolation;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;

/** Publishing needs excerpt and metaDescription (D9). */
public class PublishRequirementsNotMetException extends ApiException {

  private final transient Set<String> missingFields;

  public PublishRequirementsNotMetException(Set<String> missingFields) {
    super(
      ErrorCode.VALIDATION_FAILED,
      HttpStatus.BAD_REQUEST,
      "Cannot publish, missing fields: " + new TreeSet<>(missingFields),
      new TreeSet<>(missingFields)
        .stream()
        .map(field -> new FieldViolation(field, FieldErrorCode.NOT_BLANK))
        .toList()
    );
    this.missingFields = Set.copyOf(missingFields);
  }

  public Set<String> getMissingFields() {
    return missingFields;
  }
}
