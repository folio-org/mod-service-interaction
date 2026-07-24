package org.folio.servint.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.folio.servint.web.InvalidPurgeFlagException;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * folio-spring's generated TenantAttributes initializes purge to true, so the
 * bound DTO cannot distinguish an omitted flag from an explicit purge=true —
 * the destructive default review finding F-32 flagged (ADR-012, REQ-020 AC6).
 * This advice restores the wire truth: when the JSON body carries no non-null
 * "purge" member, the bound attribute is reset to null so the controller can
 * reject flagless blank-module_to bodies instead of purging the tenant.
 * Review finding F-37 tightened the discriminator: only exactly one top-level
 * JSON Boolean "purge" member counts as explicit — coercible scalars and
 * duplicate members are rejected 400 before binding can destroy anything.
 */
@RestControllerAdvice
public class TenantPurgeFlagAdvice extends RequestBodyAdviceAdapter {

  /** How the purge member appeared on the wire, before any binding default. */
  private enum PurgeWire { ABSENT, EXPLICIT_BOOLEAN, INVALID }

  private final JsonMapper jsonMapper;

  public TenantPurgeFlagAdvice(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public boolean supports(MethodParameter methodParameter, Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return TenantAttributes.class.equals(targetType);
  }

  @Override
  public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
      Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
    byte[] rawBody = inputMessage.getBody().readAllBytes();
    PurgeWire purge = purgeOnTheWire(rawBody);
    if (purge == PurgeWire.INVALID) {
      throw new InvalidPurgeFlagException();
    }
    return new WireBody(inputMessage.getHeaders(), rawBody, purge == PurgeWire.EXPLICIT_BOOLEAN);
  }

  @Override
  public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
      Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
    if (body instanceof TenantAttributes attributes
        && inputMessage instanceof WireBody wireBody && !wireBody.purgeExplicit()) {
      attributes.setPurge(null);
    }
    return body;
  }

  /**
   * Token-stream walk of the raw body (review F-37). readTree would collapse
   * duplicate purge members last-one-wins and binding would coerce "true"/1
   * to Boolean — both shapes can purge a tenant the caller never explicitly
   * asked to purge, so anything other than exactly one top-level Boolean
   * purge member is INVALID and rejected before binding. A single null purge
   * keeps the omitted-flag semantics (F-32), nested purge members are
   * invisible at the top level, and malformed JSON (or a non-object root) is
   * reported as explicit so this advice never interferes with the
   * converter's own malformed-json 400 path (D-22).
   */
  private PurgeWire purgeOnTheWire(byte[] rawBody) {
    try (JsonParser parser = jsonMapper.createParser(rawBody)) {
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return PurgeWire.EXPLICIT_BOOLEAN;
      }
      return classify(scanTopLevelPurge(parser));
    } catch (JacksonException e) {
      return PurgeWire.EXPLICIT_BOOLEAN;
    }
  }

  /** Count of top-level {@code purge} members and the value token of the last one seen. */
  private record PurgeMembers(int count, JsonToken value) { }

  /** Walks the top-level members, recording every {@code purge} occurrence. */
  private static PurgeMembers scanTopLevelPurge(JsonParser parser) {
    int count = 0;
    JsonToken value = null;
    while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
      String member = parser.currentName();
      JsonToken memberValue = parser.nextToken();
      if ("purge".equals(member)) {
        count++;
        value = memberValue;
      }
      parser.skipChildren();
    }
    return new PurgeMembers(count, value);
  }

  /** Only exactly one top-level Boolean purge is explicit; a single null is absent; anything else invalid. */
  private static PurgeWire classify(PurgeMembers purge) {
    if (purge.count() == 0 || (purge.count() == 1 && purge.value() == JsonToken.VALUE_NULL)) {
      return PurgeWire.ABSENT;
    }
    if (purge.count() == 1
        && (purge.value() == JsonToken.VALUE_TRUE || purge.value() == JsonToken.VALUE_FALSE)) {
      return PurgeWire.EXPLICIT_BOOLEAN;
    }
    return PurgeWire.INVALID;
  }

  private record WireBody(HttpHeaders wireHeaders, byte[] rawBody, boolean purgeExplicit)
      implements HttpInputMessage {

    @Override
    public InputStream getBody() {
      return new ByteArrayInputStream(rawBody);
    }

    @Override
    public HttpHeaders getHeaders() {
      return wireHeaders;
    }
  }
}
