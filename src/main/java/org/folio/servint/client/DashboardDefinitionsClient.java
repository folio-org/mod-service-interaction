package org.folio.servint.client;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Fetches /dashboard/definitions from ONE implementor of the dashboard
 * interface, addressed through the Okapi gateway by X-Okapi-Module-Id
 * (ADR-008). Tenant/token headers come from the caller's context via
 * folio-spring's enrichment.
 */
@HttpExchange
public interface DashboardDefinitionsClient {

  @GetExchange("dashboard/definitions")
  List<Map<String, Object>> getDefinitions(@RequestHeader("X-Okapi-Module-Id") String moduleId);
}
