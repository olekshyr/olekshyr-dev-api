package dev.olekshyr.api.shared;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Page envelope with Spring Data's {@code via_dto} shape (D4). A plain record,
 * so it caches safely, unlike {@code PageImpl}.
 */
public record PageResponse<T>(List<T> content, PageMetadata page) {
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
    int number,
    int size,
    long totalElements,
    int totalPages
  ) {}
}
