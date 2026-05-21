package dev.olekshyr.api.shared;

public enum ErrorCode {
  VALIDATION_FAILED("error.validation.failed"),
  NOT_FOUND("error.not_found"),
  INTERNAL_ERROR("error.internal");

  private final String messageKey;

  ErrorCode(String messageKey) {
    this.messageKey = messageKey;
  }

  public String messageKey() {
    return messageKey;
  }
}
