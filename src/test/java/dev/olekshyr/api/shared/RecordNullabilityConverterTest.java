package dev.olekshyr.api.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RecordNullabilityConverterTest {

  record Inner(String value) {}

  record Outer(
    String name,
    @Nullable String note,
    @io.swagger.v3.oas.annotations.media.Schema(description = "The inner part.")
    @Nullable
    Inner inner,
    @Nullable SupportedLocale locale,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String omitted
  ) {}

  private static Map<String, Schema> schemas() {
    return schemas(JsonInclude.Include.ALWAYS);
  }

  private static Map<String, Schema> schemas(JsonInclude.Include inclusion) {
    ModelConverters converters = new ModelConverters(true);
    converters.addConverter(new RecordNullabilityConverter(inclusion));
    return converters.readAll(new AnnotatedType(Outer.class));
  }

  private static Schema<?> property(Map<String, Schema> schemas, String name) {
    return (Schema<?>) schemas.get("Outer").getProperties().get(name);
  }

  /** JSON Schema checks enum on its own, so null must be listed there too. */
  @Test
  void nullableEnumListsNullAsAValue() {
    Schema<?> locale = property(schemas(), "locale");

    assertThat(locale.getTypes()).containsExactly("string", "null");
    assertThat(new ArrayList<Object>(locale.getEnum()))
      .containsExactly("en", "uk", "ja", null);
  }

  @Test
  void componentThatOmitsNullsIsOnlyOptional() {
    assertThat(property(schemas(), "omitted").getTypes())
      .containsExactly("string");
  }

  @Test
  void mapperThatOmitsNullsMakesNullableComponentsOnlyOptional() {
    Map<String, Schema> schemas = schemas(JsonInclude.Include.NON_NULL);

    assertThat(property(schemas, "note").getTypes()).containsExactly("string");
    assertThat(schemas.get("Outer").getRequired()).containsExactly("name");
  }

  @Test
  void componentsWithoutNullableAreRequired() {
    assertThat(schemas().get("Outer").getRequired()).containsExactly("name");
  }

  @Test
  void nullableValueTakesNullAsAType() {
    Schema<?> note = (Schema<?>) schemas()
      .get("Outer")
      .getProperties()
      .get("note");

    assertThat(note.getTypes()).containsExactly("string", "null");
  }

  /** A type next to a $ref would mean "null and the referenced schema". */
  @Test
  void nullableReferenceBecomesOneOfTheReferenceOrNull() {
    Schema<?> inner = (Schema<?>) schemas()
      .get("Outer")
      .getProperties()
      .get("inner");

    assertThat(inner.get$ref()).isNull();
    assertThat(inner.getOneOf()).hasSize(2);
    assertThat(inner.getOneOf().get(0).get$ref())
      .isEqualTo("#/components/schemas/Inner");
    assertThat(inner.getOneOf().get(1).getTypes()).containsExactly("null");
    assertThat(inner.getDescription()).isEqualTo("The inner part.");
    assertThat(inner.getOneOf().get(0).getDescription()).isNull();
  }
}
