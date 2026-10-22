package dev.olekshyr.api.shared;

public enum ErrorCode {
  VALIDATION_FAILED("error.validation.failed"),
  NOT_FOUND("error.not_found"),
  TRANSLATION_NOT_AVAILABLE("error.translation_not_available"),
  UNSUPPORTED_REQUEST("error.unsupported_request"),
  DUPLICATE_SLUG("error.duplicate_slug"),
  SLUG_FROZEN("error.slug_frozen"),
  TAG_IN_USE("error.tag_in_use"),
  CONFLICT("error.conflict"),
  REQUEST_REJECTED("error.request_rejected"),
  INTERNAL_ERROR("error.internal");

  private final String messageKey;

  ErrorCode(String messageKey) {
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
