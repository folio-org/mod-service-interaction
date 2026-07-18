package org.folio.servint.service.widget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Port of the legacy UtilityService JSON helpers: classpath JSON loading and
 * everit-json-schema validation (same library as legacy, so widget
 * definitions validate identically).
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class JsonSchemaService {

  private final JsonMapper jsonMapper;
  private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

  @SuppressWarnings("unchecked")
  public Map<String, Object> getJsonFileFromClassPath(String classPath) {
    try (var stream = resolver.getResource("classpath:" + classPath).getInputStream()) {
      return jsonMapper.readValue(stream, Map.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public java.util.List<Map<String, Object>> getJsonFilesFromClassPath(String classPath) {
    try {
      var resources = resolver.getResources("classpath*:" + classPath);
      var parsed = new java.util.ArrayList<Map<String, Object>>();
      for (var resource : resources) {
        try (var stream = resource.getInputStream()) {
          parsed.add(jsonMapper.readValue(stream, Map.class));
        }
      }
      return parsed;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** json and schema may each be a parsed Map or a raw JSON string. */
  public boolean validateJsonAgainstSchema(Object json, Object schema) {
    var rawSchema = toJsonObject(schema);
    log.debug("Validating against schema ({})", rawSchema.optString("title"));
    var loadedSchema = SchemaLoader.load(rawSchema);
    try {
      loadedSchema.validate(toJsonObject(json));
      return true;
    } catch (ValidationException e) {
      logValidationException(e, 0);
      return false;
    }
  }

  private JSONObject toJsonObject(Object value) {
    if (value instanceof String s) {
      return new JSONObject(s);
    }
    if (value instanceof Map<?, ?> map) {
      return new JSONObject(map);
    }
    throw new IllegalArgumentException("Cannot convert " + value + " to a JSON object");
  }

  private void logValidationException(ValidationException e, int tabLevel) {
    log.error("{}{}", "\t".repeat(tabLevel), e.getMessage());
    for (var cause : e.getCausingExceptions()) {
      logValidationException(cause, tabLevel + 1);
    }
  }
}
