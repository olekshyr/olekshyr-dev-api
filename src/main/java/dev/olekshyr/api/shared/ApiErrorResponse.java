package dev.olekshyr.api.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
  String code,
  String message,
  Instant timestamp,
  String path,
  String traceId,
  List<ApiFieldError> details
) {
  public ApiErrorResponse {
    if (details != null) {
      details = List.copyOf(details);
    }
  }
}
