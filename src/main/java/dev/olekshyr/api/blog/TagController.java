package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiError;
import dev.olekshyr.api.shared.ErrorCode;
import dev.olekshyr.api.shared.SupportedLocale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public tag list (D29); the locale comes from Accept-Language (D1). */
@Tag(name = "Tags", description = "The tags of the posts in the list.")
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
class TagController {

  private final TagQueryService tags;

  @GetMapping
  @Operation(
    operationId = "listPublicTags",
    summary = "List the tags in use",
    description = "Every tag that the post list with the same `include` " +
    "and locale would return at least one post for, sorted by slug. Not " +
    "paginated: the tag set is small."
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = "`include` has an unsupported value; `details` names it " +
    "(`INVALID_VALUE`)."
  )
  List<TagSummaryResponse> list(
    @Parameter(
      description = "`untranslated` counts every post visible in any locale, " +
      "as the post list does with the same value.",
      schema = @Schema(type = "string", allowableValues = { "untranslated" })
    ) @RequestParam(required = false) @Nullable ListInclude include,
    Locale locale
  ) {
    return tags.listInUse(
      SupportedLocale.from(locale),
      include == ListInclude.UNTRANSLATED
    );
  }
}
