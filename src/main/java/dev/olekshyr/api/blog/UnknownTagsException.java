package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.FieldViolation;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Setting a post's tags to slugs that don't exist; one violation per slug. */
class UnknownTagsException extends ApiException {

  UnknownTagsException(List<FieldViolation> violations) {
    super(
      ErrorCode.VALIDATION_FAILED,
      HttpStatus.BAD_REQUEST,
      "Unknown tags: " + violations,
      violations
    );
  }
}
