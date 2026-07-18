package org.folio.servint;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.folio.servint.service.widget.WidgetDefinitionService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regression for re-review finding F-23: the per-tenant widget-definition
 * harvest cache must be bounded. Floods the cache with more distinct
 * tenants than the default bound (500) and asserts the bound holds, that
 * explicit lifecycle eviction still works, and that the cache's Micrometer
 * meters are published — both in the in-process registry and, per review
 * finding F-36, served over the wire on the actuator metrics endpoint
 * under the module's /admin surface (which is why this IT boots a real
 * server port instead of the mock web environment).
 *
 * <p>Unlike {@link WidgetCacheTenantIsolationIT} this IT never enables a
 * tenant: caching a tenant entry does not require an existing schema, so
 * each simulated tenant is just a per-call FolioExecutionContext plus an
 * Okapi stub reporting no dashboard interface (which also keeps the
 * harvest DB-free — occupancy, not content, is what is measured here).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WidgetCacheCapacityIT {

  private static final int DEFAULT_MAX_TENANTS = 500;
  private static final int SIMULATED_TENANTS = 600;
  private static final String CACHE_METER_TAG = "widget-definition-tenant-cache";

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  static final WireMockServer OKAPI = new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  private WidgetDefinitionService widgetDefinitionService;

  @Autowired
  private FolioModuleMetadata moduleMetadata;

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  private JsonMapper jsonMapper;

  @LocalServerPort
  private int port;

  @BeforeAll
  static void startOkapi() {
    OKAPI.start();
    OKAPI.stubFor(WireMock.get(urlMatching("/_/proxy/tenants/[a-z0-9]+/interfaces"))
        .willReturn(okJson("[]")));
  }

  @AfterAll
  static void stopOkapi() {
    OKAPI.stop();
  }

  private static String tenantId(int i) {
    return String.format("captenant%03d", i);
  }

  /** Runs a harvest as the given tenant, occupying one cache entry. */
  private void fetchDefinitionsAs(String tenantId) {
    Map<String, Collection<String>> okapiHeaders = Map.of(
        XOkapiHeaders.TENANT, List.of(tenantId),
        XOkapiHeaders.URL, List.of(OKAPI.baseUrl()));
    try (var ignored = new FolioExecutionContextSetter(moduleMetadata, okapiHeaders)) {
      widgetDefinitionService.fetchDefinitions(null, null, null);
    }
  }

  @Test
  @Order(1)
  void sizeBoundHoldsUnderSixHundredTenantFlood() {
    for (int i = 0; i < SIMULATED_TENANTS; i++) {
      fetchDefinitionsAs(tenantId(i));
    }
    long cached = widgetDefinitionService.cachedTenantCount();
    // The bound holds — and the cache genuinely retains entries rather than
    // thrashing (600 inserts must leave far more than a handful cached).
    assertThat(cached).isLessThanOrEqualTo(DEFAULT_MAX_TENANTS);
    assertThat(cached).isGreaterThan(400);
  }

  @Test
  @Order(2)
  void explicitEvictionStillRemovesACachedTenant() {
    var victim = tenantId(SIMULATED_TENANTS - 1);
    // Re-touch first: the newest insert always occupies Caffeine's admission
    // window, so the victim is guaranteed cached when evicted below.
    fetchDefinitionsAs(victim);
    long before = widgetDefinitionService.cachedTenantCount();

    widgetDefinitionService.evictTenant(victim);
    assertThat(widgetDefinitionService.cachedTenantCount()).isEqualTo(before - 1);

    // Evicting a never-cached tenant stays a no-op.
    widgetDefinitionService.evictTenant("captenantnevercached");
    assertThat(widgetDefinitionService.cachedTenantCount()).isEqualTo(before - 1);
  }

  @Test
  @Order(3)
  void cacheMetricsAreExposed() {
    var sizeGauge = meterRegistry.find("cache.size").tag("cache", CACHE_METER_TAG).gauge();
    assertThat(sizeGauge).isNotNull();
    assertThat(sizeGauge.value()).isGreaterThan(0);

    // cachedTenantCount() already forced pending maintenance, so the flood's
    // ~100 size evictions are recorded in the stats this counter polls —
    // asserting > 0 is deterministic here.
    var evictions = meterRegistry.find("cache.evictions").tag("cache", CACHE_METER_TAG).functionCounter();
    assertThat(evictions).isNotNull();
    assertThat(evictions.count()).isGreaterThan(0);
  }

  @Test
  @Order(4)
  void cacheMetricsAreServedOverTheAdminSurface() {
    // Review F-36: the meters above must be operationally scrapeable, not
    // just registered — fetched here through the real HTTP server, tag-
    // filtered to the widget-definition cache, on the /admin base path.
    JsonNode size = fetchMetricOverHttp("cache.size");
    assertThat(size.path("name").asString()).isEqualTo("cache.size");
    assertThat(size.path("measurements").get(0).path("value").asDouble()).isGreaterThan(0);

    JsonNode evictions = fetchMetricOverHttp("cache.evictions");
    assertThat(evictions.path("name").asString()).isEqualTo("cache.evictions");
    assertThat(evictions.path("measurements").get(0).path("value").asDouble()).isGreaterThan(0);
  }

  private JsonNode fetchMetricOverHttp(String meterName) {
    String body = RestClient.create()
        .get()
        .uri("http://localhost:{port}/admin/metrics/{meter}?tag=cache:{cache}",
            port, meterName, CACHE_METER_TAG)
        .retrieve()
        .body(String.class);
    return jsonMapper.readTree(body);
  }
}
