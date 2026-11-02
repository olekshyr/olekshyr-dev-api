package dev.olekshyr.api.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

@Schema(
  description = "Body of every error response except the empty-body 401/403."
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
  @Schema(
    implementation = ErrorCode.class,
    description = "Stable error code: branch on this, never on `message`."
  )
  String code,
  @Schema(
    description = "Localized from `Accept-Language`; meant to be shown as-is."
  )
  String message,
  Instant timestamp,
  @Schema(
    description = "Request path; left out only outside a servlet request.",
    example = "/api/v1/posts/hello-world"
  )
  @Nullable
  String path,
  @Schema(
    description = "OpenTelemetry trace id, left out when the request wasn't " +
    "traced. Quote it when reporting an `INTERNAL_ERROR`."
  )
  @Nullable
  String traceId,
  @Schema(
    description = "One entry per invalid field, on some `VALIDATION_FAILED` " +
    "errors (not on malformed JSON or a bad path value); left out otherwise."
  )
  @Nullable
  List<ApiFieldError> details
) {
  public ApiErrorResponse {
    if (details != null) {
      details = List.copyOf(details);
    }
  }
}
