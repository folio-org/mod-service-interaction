package org.folio.servint.web;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.integration.XOkapiHeaders;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Port of the legacy hasAuthority('okapi.<permission>') check: Okapi passes
 * the caller's granted desired-permissions as a JSON array in
 * X-Okapi-Permissions; the legacy Spring Security layer exposed each entry
 * as an authority with the okapi. prefix.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class OkapiAuthorityService {

  private final FolioExecutionContext folioExecutionContext;
  private final JsonMapper jsonMapper;

  public boolean hasAuthority(String permission) {
    var headerValues = folioExecutionContext.getOkapiHeaders().get(XOkapiHeaders.PERMISSIONS);
    if (headerValues == null) {
      return false;
    }
    for (var headerValue : headerValues) {
      try {
        List<?> permissions = jsonMapper.readValue(headerValue, List.class);
        if (permissions.contains(permission)) {
          return true;
        }
      } catch (RuntimeException e) {
        log.warn("Unparseable {} header: {}", XOkapiHeaders.PERMISSIONS, headerValue);
      }
    }
    return false;
  }
}
