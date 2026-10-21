package dev.olekshyr.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

class PageResponseTest {

  @Test
  void copiesContentAndPageMetadata() {
    PageResponse<String> page = PageResponse.of(
      new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5)
    );

    assertThat(page.content()).containsExactly("a", "b");
    assertThat(page.page())
      .isEqualTo(new PageResponse.PageMetadata(1, 2, 5, 3));
  }

  @Test
  void serializesInSpringDataShape() {
    PageResponse<String> page = PageResponse.of(
      new PageImpl<>(List.of("a"), PageRequest.of(0, 10), 1)
    );

    String json = JsonMapper.builder().build().writeValueAsString(page);

    assertThat(json)
      .isEqualTo(
        "{\"content\":[\"a\"],\"page\":{\"number\":0,\"size\":10,\"totalElements\":1,\"totalPages\":1}}"
      );
  }
}
