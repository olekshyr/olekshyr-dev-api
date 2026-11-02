package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiError;
import dev.olekshyr.api.shared.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin tag API (ADMIN_API_KEY); every tag with all its names (D15). */
@Tag(
  name = "Admin tags",
  description = "Tags by numeric id, with every name. Posts reference them " +
  "by slug."
)
@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
class TagAdminController {

  private static final String BAD_ID = "`id` isn't a number.";
  private static final String NO_TAG = "No tag with this id.";
  private static final String BAD_BODY =
    "The body is malformed (including a `names` key other than `en`, `uk`, " +
    "`ja`), or a field breaks a rule (`details`: `NOT_BLANK`, `SIZE` or " +
    "`PATTERN` for `slug`; `PATTERN` for `color`; `NOT_NULL` for `names`; " +
    "`NOT_BLANK` or `SIZE` for a name).";
  private static final String SLUG_TAKEN = "Another tag has this slug.";
  private static final String NAME_TAKEN =
    "Another tag has one of these names in the same locale, or a concurrent " +
    "request took the slug first.";

  private final TagAdminService tags;

  @GetMapping
  @Operation(
    operationId = "listTags",
    summary = "List tags",
    description = "Every tag, sorted by slug. Not paginated: it's a small " +
    "fixed vocabulary."
  )
  List<AdminTagResponse> list() {
    return tags.list();
  }

  /** Where POST's Location points. */
  @GetMapping("/{id}")
  @Operation(operationId = "getTag", summary = "Get a tag")
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NO_TAG
  )
  AdminTagResponse get(@PathVariable long id) {
    return tags.get(id);
  }

  @PostMapping
  @Operation(operationId = "createTag", summary = "Create a tag")
  @ApiResponse(
    responseCode = "201",
    description = "Created.",
    useReturnTypeSchema = true,
    headers = @Header(
      name = "Location",
      description = "URL of the new tag.",
      schema = @Schema(type = "string", format = "uri")
    )
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_BODY
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.DUPLICATE_SLUG,
    when = SLUG_TAKEN
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.CONFLICT,
    when = NAME_TAKEN
  )
  ResponseEntity<AdminTagResponse> create(
    @Valid @RequestBody UpsertTagRequest request
  ) {
    AdminTagResponse created = tags.create(request);
    return ResponseEntity
      .created(CreatedLocation.of(created.id()))
      .body(created);
  }

  @PutMapping("/{id}")
  @Operation(
    operationId = "updateTag",
    summary = "Replace a tag",
    description = "Replaces slug, color and names. Unlike a post's, a tag's " +
    "slug can always change; posts follow it."
  )
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID + " Or " + BAD_BODY
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NO_TAG
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.DUPLICATE_SLUG,
    when = SLUG_TAKEN
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.CONFLICT,
    when = NAME_TAKEN
  )
  AdminTagResponse update(
    @PathVariable long id,
    @Valid @RequestBody UpsertTagRequest request
  ) {
    return tags.update(id, request);
  }

  @DeleteMapping("/{id}")
  @Operation(
    operationId = "deleteTag",
    summary = "Delete a tag",
    description = "Only a tag no post uses, deleted posts included."
  )
  @ApiResponse(responseCode = "204", description = "Deleted.")
  @ApiError(
    status = HttpStatus.BAD_REQUEST,
    code = ErrorCode.VALIDATION_FAILED,
    when = BAD_ID
  )
  @ApiError(
    status = HttpStatus.NOT_FOUND,
    code = ErrorCode.NOT_FOUND,
    when = NO_TAG
  )
  @ApiError(
    status = HttpStatus.CONFLICT,
    code = ErrorCode.TAG_IN_USE,
    when = "A post still has this tag; deleted posts count too."
  )
  ResponseEntity<Void> delete(@PathVariable long id) {
    tags.delete(id);
    return ResponseEntity.noContent().build();
  }
}
