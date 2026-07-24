package org.folio.servint.service.widget;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.folio.servint.domain.entity.WidgetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Branch coverage for {@link WidgetDefinitionService#validateDefinition}: the
 * two-stage schema gate (generic definition schema, then the resolved
 * WidgetType's schema) with every rejection path. Collaborators are mocked so
 * the branches are driven directly, independent of federation wiring.
 */
class WidgetDefinitionValidateTest {

  private static final Map<String, Object> GENERIC_SCHEMA = Map.of("generic", true);
  private static final String TYPE_SCHEMA = "{\"type\":\"object\"}";

  private JsonSchemaService jsonSchemas;
  private WidgetTypeService widgetTypes;
  private WidgetDefinitionService service;

  @BeforeEach
  void setUp() {
    jsonSchemas = mock(JsonSchemaService.class);
    widgetTypes = mock(WidgetTypeService.class);
    when(jsonSchemas.getJsonFileFromClassPath(
        "sample_data/generic_widget_definition_schema.json")).thenReturn(GENERIC_SCHEMA);
    service = new WidgetDefinitionService(null, null, widgetTypes, jsonSchemas, null, null,
        new SimpleMeterRegistry(), 500L, Duration.ofMinutes(30));
  }

  private Map<String, Object> definition() {
    return Map.of(
        "name", "ERM Agreements", "version", "1.0",
        "type", Map.of("name", "TestType", "version", "1.0"),
        "definition", Map.of("anything", true));
  }

  private WidgetType compatibleType() {
    var type = new WidgetType();
    type.setName("TestType");
    type.setTypeVersion("1.0");
    type.setSchema(TYPE_SCHEMA);
    return type;
  }

  @Test
  void rejectsWhenGenericSchemaFails() {
    when(jsonSchemas.validateJsonAgainstSchema(any(), eq(GENERIC_SCHEMA))).thenReturn(false);
    assertFalse(service.validateDefinition(definition()));
  }

  @Test
  void rejectsWhenTypeInformationIsMissing() {
    when(jsonSchemas.validateJsonAgainstSchema(any(), eq(GENERIC_SCHEMA))).thenReturn(true);
    // no "type" key -> typeName/typeVersion null -> "Missing information" branch
    assertFalse(service.validateDefinition(Map.of("name", "X", "version", "1.0")));
  }

  @Test
  void rejectsWhenNoCompatibleTypeExists() {
    when(jsonSchemas.validateJsonAgainstSchema(any(), eq(GENERIC_SCHEMA))).thenReturn(true);
    when(widgetTypes.latestCompatibleType("TestType", "1.0")).thenReturn(null);
    assertFalse(service.validateDefinition(definition()));
  }

  @Test
  void rejectsWhenDefinitionFailsTheTypeSchema() {
    when(jsonSchemas.validateJsonAgainstSchema(any(), eq(GENERIC_SCHEMA))).thenReturn(true);
    when(widgetTypes.latestCompatibleType("TestType", "1.0")).thenReturn(compatibleType());
    when(jsonSchemas.validateJsonAgainstSchema(any(), eq(TYPE_SCHEMA))).thenReturn(false);
    assertFalse(service.validateDefinition(definition()));
  }

  @Test
  void acceptsWhenBothSchemasPass() {
    when(jsonSchemas.validateJsonAgainstSchema(any(), eq(GENERIC_SCHEMA))).thenReturn(true);
    when(widgetTypes.latestCompatibleType("TestType", "1.0")).thenReturn(compatibleType());
    when(jsonSchemas.validateJsonAgainstSchema(any(), eq(TYPE_SCHEMA))).thenReturn(true);
    assertTrue(service.validateDefinition(definition()));
  }
}
