package dev.olekshyr.api.blog;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
class TagAdminController {

  private final TagAdminService tags;

  @GetMapping
  List<AdminTagResponse> list() {
    return tags.list();
  }

  /** Where POST's Location points. */
  @GetMapping("/{id}")
  AdminTagResponse get(@PathVariable long id) {
    return tags.get(id);
  }

  @PostMapping
  ResponseEntity<AdminTagResponse> create(
    @Valid @RequestBody UpsertTagRequest request
  ) {
    AdminTagResponse created = tags.create(request);
    return ResponseEntity
      .created(CreatedLocation.of(created.id()))
      .body(created);
  }

  @PutMapping("/{id}")
  AdminTagResponse update(
    @PathVariable long id,
    @Valid @RequestBody UpsertTagRequest request
  ) {
    return tags.update(id, request);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable long id) {
    tags.delete(id);
    return ResponseEntity.noContent().build();
  }
}
