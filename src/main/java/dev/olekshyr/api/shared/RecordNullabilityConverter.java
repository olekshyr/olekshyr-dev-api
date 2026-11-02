package dev.olekshyr.api.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Documents a record's nullability from JSpecify: a component without
 * {@code @Nullable} is required, and one with it may be null. Where nulls are
 * omitted ({@code @JsonInclude} on the component or the record, else the
 * mapper's default), a null component is left out instead, so it's only
 * optional there.
 */
class RecordNullabilityConverter implements ModelConverter {

  private static final String REF_PREFIX = "#/components/schemas/";

  /** Inclusions that leave a null value out of the JSON. */
  private static final Set<JsonInclude.Include> OMITTING_NULLS = Set.of(
    JsonInclude.Include.NON_NULL,
    JsonInclude.Include.NON_ABSENT,
    JsonInclude.Include.NON_EMPTY,
    JsonInclude.Include.NON_DEFAULT
  );

  private final JsonInclude.Include defaultInclusion;

  /** defaultInclusion: the mapper's default value inclusion. */
  RecordNullabilityConverter(JsonInclude.Include defaultInclusion) {
    this.defaultInclusion = defaultInclusion;
  }

  @Override
  public Schema resolve(
    AnnotatedType type,
    ModelConverterContext context,
    Iterator<ModelConverter> chain
  ) {
    if (!chain.hasNext()) return null;
    Schema<?> resolved = chain.next().resolve(type, context, chain);
    if (resolved == null || type.getType() == null) return resolved;
    Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
    if (!raw.isRecord()) return resolved;

    Schema<?> model = resolved.get$ref() == null
      ? resolved
      : context
        .getDefinedModels()
        .get(resolved.get$ref().substring(REF_PREFIX.length()));
    if (model == null || model.getProperties() == null) return resolved;

    for (RecordComponent component : raw.getRecordComponents()) {
      String name = component.getName();
      Schema<?> property = model.getProperties().get(name);
      if (property == null) continue;
      if (!component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
        if (
          model.getRequired() == null || !model.getRequired().contains(name)
        ) {
          model.addRequiredItem(name);
        }
      } else if (!omitsNulls(raw, component)) {
        model.getProperties().put(name, nullable(property));
      }
    }
    return resolved;
  }

  /** The closest setting wins: the component, then the record, then default. */
  private boolean omitsNulls(Class<?> record, RecordComponent component) {
    JsonInclude.Include inclusion = defaultInclusion;
    for (JsonInclude include : new JsonInclude[] {
      component.getAccessor().getAnnotation(JsonInclude.class),
      record.getAnnotation(JsonInclude.class),
    }) {
      if (
        include != null && include.value() != JsonInclude.Include.USE_DEFAULTS
      ) {
        inclusion = include.value();
        break;
      }
    }
    return OMITTING_NULLS.contains(inclusion);
  }

  /**
   * OpenAPI 3.1 has no nullable keyword: null joins the property's types. A
   * reference can't take a type next to it (that would mean "null and the
   * referenced schema"), so it becomes one of the reference or null.
   */
  private static Schema<?> nullable(Schema<?> property) {
    if (property.getOneOf() != null) return property;
    if (property.get$ref() != null) {
      // The field's own docs belong on the field, not on one of its branches
      Schema<?> field = new Schema<>()
        .description(property.getDescription())
        .oneOf(List.of(property, new Schema<>().types(Set.of("null"))));
      property.setDescription(null);
      if (property.getExample() != null) {
        // setExample(null) would write "example": null, so only move it
        field.setExample(property.getExample());
        property.setExample(null);
        property.setExampleSetFlag(false);
      }
      return field;
    }
    Set<String> types = new LinkedHashSet<>();
    if (property.getTypes() != null) {
      types.addAll(property.getTypes());
    } else if (property.getType() != null) {
      types.add(property.getType());
    }
    types.add("null");
    property.setTypes(types);
    // enum is checked on its own, so null must be one of the values too
    if (property.getEnum() != null && !property.getEnum().contains(null)) {
      List<Object> values = new ArrayList<>(property.getEnum());
      values.add(null);
      @SuppressWarnings("unchecked")
      Schema<Object> withNull = (Schema<Object>) property;
      withNull.setEnum(values);
    }
    return property;
  }
}
