package dev.olekshyr.api.shared;

/**
 * Field-level codes for checks the services make, beyond Bean Validation
 * (D21). Like {@link ErrorCode}, the names are a stable contract (D17).
 */
public enum FieldErrorCode {
  NOT_BLANK("error.field.not_blank"),
  UNKNOWN_TAG("error.field.unknown_tag"),
  /** A query parameter or path segment that doesn't parse, e.g. ?sort=x. */
  INVALID_VALUE("error.field.invalid_value");

  private final String messageKey;

  FieldErrorCode(String messageKey) {
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
