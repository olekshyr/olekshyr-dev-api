package dev.olekshyr.api.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Page envelope with Spring Data's {@code via_dto} shape (D4). A plain record,
 * so it caches safely, unlike {@code PageImpl}.
 */
public record PageResponse<T>(
  @Schema(description = "This page's items; empty past the last page.")
  List<T> content,
  PageMetadata page
) {
  public PageResponse {
    content = List.copyOf(content);
  }

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
      page.getContent(),
      new PageMetadata(
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages()
      )
    );
  }

  public record PageMetadata(
    @Schema(description = "Zero-based page index, as corrected.") int number,
    @Schema(description = "Page size, as corrected (1–50).") int size,
    long totalElements,
    int totalPages
  ) {}
}
