package org.folio.servint.service.widget;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import org.folio.servint.client.DashboardDefinitionsClient;
import org.folio.servint.client.OkapiProxyClient;
import org.folio.spring.FolioExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Port of the legacy WidgetDefinitionService federation logic. Unlike the
 * legacy module-level static cache (tenant-blind — review finding F-07),
 * harvest state is keyed per tenant: each tenant owns its implementor set
 * and harvested definitions, refreshed when that tenant's
 * dashboard-interface implementor set changes or its cache is empty —
 * including the legacy quirk that the cached implementor set is only
 * recorded when empty, so a permanently-changed implementor set keeps
 * refetching. Tenant entries are evicted on tenant lifecycle events
 * (upgrade and purge, via ServintTenantService). Validation (generic
 * schema, then the latest compatible WidgetType's schema), first-wins name
 * de-duplication, and highest-compatible-minor-per-major selection are the
 * wire contract.
 *
 * <p>The tenant cache is bounded (re-review finding F-23): at most
 * {@code folio.widgets.definition-cache.max-tenants} entries (default 500,
 * LRU-style size eviction) each expiring after
 * {@code folio.widgets.definition-cache.expire-after-access} of inactivity
 * (default 30m); both are overridable via {@code
 * folio.widgets.definition-cache.*}. Cache health is exposed as Micrometer
 * {@code cache.*} meters tagged {@code cache=widget-definition-tenant-cache}.
 * Explicit eviction on tenant lifecycle events is unchanged.
 */
@Log4j2
@Service
public class WidgetDefinitionService {

  /** Per-tenant harvest state: implementor change-detector + definitions. */
  private static final class TenantCache {
    private Set<String> dashboardImplementors = new LinkedHashSet<>();
    private List<Map<String, Object>> definitions = new ArrayList<>();
  }

  private final OkapiProxyClient okapiProxy;
  private final DashboardDefinitionsClient definitionsClient;
  private final WidgetTypeService widgetTypes;
  private final JsonSchemaService jsonSchemas;
  private final VersionService versions;
  private final FolioExecutionContext folioExecutionContext;

  private final Cache<String, TenantCache> tenantCaches;
  private Map<String, Object> genericDefinitionSchema;

  public WidgetDefinitionService(OkapiProxyClient okapiProxy,
                                 DashboardDefinitionsClient definitionsClient,
                                 WidgetTypeService widgetTypes,
                                 JsonSchemaService jsonSchemas,
                                 VersionService versions,
                                 FolioExecutionContext folioExecutionContext,
                                 MeterRegistry meterRegistry,
                                 @Value("${folio.widgets.definition-cache.max-tenants:500}")
                                 long maxTenants,
                                 @Value("${folio.widgets.definition-cache.expire-after-access:30m}")
                                 Duration expireAfterAccess) {
    this.okapiProxy = okapiProxy;
    this.definitionsClient = definitionsClient;
    this.widgetTypes = widgetTypes;
    this.jsonSchemas = jsonSchemas;
    this.versions = versions;
    this.folioExecutionContext = folioExecutionContext;
    this.tenantCaches = Caffeine.newBuilder()
        .maximumSize(maxTenants)
        .expireAfterAccess(expireAfterAccess)
        .recordStats()
        .build();
    CaffeineCacheMetrics.monitor(meterRegistry, tenantCaches, "widget-definition-tenant-cache");
  }

  public synchronized List<Map<String, Object>> fetchDefinitions(String name, String nameLike, String version) {
    var cache = tenantCaches.get(folioExecutionContext.getTenantId(), tenant -> new TenantCache());
    if (implementorsChanged(cache) || cache.definitions.isEmpty()) {
      // Legacy getMultiInterface re-queries the provider list for the fetch
      // itself; the cached set is only the change detector.
      var fetched = new ArrayList<Map<String, Object>>();
      for (var implementor : interfaceProviders()) {
        try {
          log.debug("Attempting to GET definitions from interface provider: {}", implementor);
          var incoming = definitionsClient.getDefinitions(implementor);
          resolveDefinitions(fetched, incoming);
        } catch (Exception e) {
          log.error("Error with GET from interface provider: {}. {}", implementor, e.getMessage());
        }
      }
      cache.definitions = fetched;
    }
    return getLatestCompatibleDefinitions(cache.definitions, name, nameLike, version);
  }

  /** Drops a tenant's harvest state (tenant upgrade or purge — F-07). */
  public void evictTenant(String tenantId) {
    if (tenantCaches.getIfPresent(tenantId) != null) {
      tenantCaches.invalidate(tenantId);
      log.info("Evicted widget-definition cache for tenant {}", tenantId);
    }
  }

  /**
   * Number of tenants currently cached. Forces Caffeine's pending
   * maintenance first so the size is exact (size-based eviction runs on
   * write but may be asynchronous); used by the capacity IT and for
   * operational debugging.
   */
  public long cachedTenantCount() {
    tenantCaches.cleanUp();
    return tenantCaches.estimatedSize();
  }

  /**
   * Legacy implementorsChanged, per tenant: the cached set is assigned ONLY
   * when empty; afterwards a differing fetched set reports "changed"
   * without updating the cache.
   */
  private boolean implementorsChanged(TenantCache cache) {
    var implementors = new LinkedHashSet<>(interfaceProviders());
    if (cache.dashboardImplementors.isEmpty()) {
      cache.dashboardImplementors = implementors;
      return true;
    }
    return !cache.dashboardImplementors.equals(implementors);
  }

  private List<String> interfaceProviders() {
    var tenant = folioExecutionContext.getTenantId();
    var providesCompatible = okapiProxy.getInterfaces(tenant).stream()
        .anyMatch(i -> "dashboard".equals(i.id()) && versions.compatibleVersion(i.version(), "1.0"));
    if (!providesCompatible) {
      log.debug("Environment does not provide interface: dashboard for version: ^1.0");
      return List.of();
    }
    return okapiProxy.getModules(tenant, "dashboard").stream()
        .map(OkapiProxyClient.ModuleRef::id)
        .toList();
  }

  private void resolveDefinitions(List<Map<String, Object>> definitionList,
                                  List<Map<String, Object>> incomingDefinitions) {
    var valid = incomingDefinitions.stream().filter(this::validateDefinition).toList();
    definitionList.addAll(parseOutExistingDefinitionNames(definitionList, valid));
  }

  private List<Map<String, Object>> parseOutExistingDefinitionNames(
      List<Map<String, Object>> definitionList, List<Map<String, Object>> incomingDefinitions) {
    if (definitionList.isEmpty()) {
      return incomingDefinitions;
    }
    var accepted = new ArrayList<Map<String, Object>>();
    for (var incoming : incomingDefinitions) {
      var duplicate = definitionList.stream()
          .anyMatch(d -> java.util.Objects.equals(d.get("name"), incoming.get("name")));
      if (duplicate) {
        log.error("WidgetDefinition with name: {} is already implemented by another module",
            incoming.get("name"));
      } else {
        accepted.add(incoming);
      }
    }
    return accepted;
  }

  boolean validateDefinition(Map<String, Object> widgetDefinition) {
    log.info("validateDefinition : {} v{}", widgetDefinition.get("name"), widgetDefinition.get("version"));
    var valid = false;
    if (jsonSchemas.validateJsonAgainstSchema(widgetDefinition, getGenericDefinitionSchema())) {
      var type = asMap(widgetDefinition.get("type"));
      var typeName = type == null ? null : (String) type.get("name");
      var typeVersion = type == null ? null : (String) type.get("version");
      if (typeName != null && typeVersion != null) {
        var compatibleType = widgetTypes.latestCompatibleType(typeName, typeVersion);
        if (compatibleType != null) {
          valid = jsonSchemas.validateJsonAgainstSchema(
              widgetDefinition.get("definition"), compatibleType.getSchema());
        } else {
          log.error("No compatible WidgetType found for {} v{}", typeName, typeVersion);
        }
      } else {
        log.error("Missing information, cannot resolve WidgetType");
      }
    }
    if (!valid) {
      log.error("WidgetDefinition {} (v{}) is not valid",
          widgetDefinition.get("name"), widgetDefinition.get("version"));
    }
    return valid;
  }

  private List<Map<String, Object>> getLatestCompatibleDefinitions(List<Map<String, Object>> definitions,
                                                                   String name, String nameLike,
                                                                   String version) {
    var filtered = definitions.stream()
        .filter(d -> name == null || name.equalsIgnoreCase((String) d.get("name")))
        .filter(d -> nameLike == null
            || Pattern.compile(nameLike, Pattern.CASE_INSENSITIVE).matcher((String) d.get("name")).find())
        .filter(d -> version == null || versions.compatibleVersion((String) d.get("version"), version))
        .toList();

    var results = new ArrayList<Map<String, Object>>();
    for (var candidate : filtered) {
      var candidateName = (String) candidate.get("name");
      var candidateVersion = (String) candidate.get("version");
      var alreadyCovered = results.stream().anyMatch(r ->
          java.util.Objects.equals(r.get("name"), candidateName)
              && versions.compatibleVersion((String) r.get("version"), candidateVersion));
      if (!alreadyCovered) {
        results.removeIf(r -> java.util.Objects.equals(r.get("name"), candidateName)
            && versions.compatibleVersion(candidateVersion, (String) r.get("version")));
        results.add(candidate);
      }
    }
    return results;
  }

  private Map<String, Object> getGenericDefinitionSchema() {
    if (genericDefinitionSchema == null) {
      genericDefinitionSchema =
          jsonSchemas.getJsonFileFromClassPath("sample_data/generic_widget_definition_schema.json");
    }
    return genericDefinitionSchema;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : null;
  }
}
