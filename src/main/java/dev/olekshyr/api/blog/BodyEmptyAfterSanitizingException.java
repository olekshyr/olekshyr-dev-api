package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.FieldErrorCode;
import dev.olekshyr.api.shared.FieldViolation;
import java.util.List;
import org.springframework.http.HttpStatus;

/** The body held only markup the sanitizer strips, so nothing would be saved. */
class BodyEmptyAfterSanitizingException extends ApiException {

  BodyEmptyAfterSanitizingException() {
    super(
      ErrorCode.VALIDATION_FAILED,
      HttpStatus.BAD_REQUEST,
      "Body is empty after sanitizing",
      List.of(new FieldViolation("body", FieldErrorCode.NOT_BLANK))
    );
  }
}
