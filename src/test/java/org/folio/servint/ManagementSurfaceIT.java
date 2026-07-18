package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Review-4 F-43 gate (DP-2(a)): the module's /admin management surface is
 * unauthenticated, so it must expose exactly {@code health} and
 * {@code metrics}. The review's reproduction flipped a logger to TRACE via
 * unauthenticated POST /admin/loggers/&lt;name&gt;; with {@code loggers}
 * dropped from {@code management.endpoints.web.exposure.include}, every
 * loggers route must 404 while the F-36 obligation — health and metrics
 * served over the wire — stays intact.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ManagementSurfaceIT {

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort
  private int port;

  @Test
  void loggersSurfaceIsNotExposed() {
    assertThat(status(HttpMethod.GET, "/admin/loggers", null))
        .as("GET /admin/loggers").isEqualTo(404);
    assertThat(status(HttpMethod.GET, "/admin/loggers/org.folio.servint", null))
        .as("GET /admin/loggers/<name>").isEqualTo(404);
  }

  @Test
  void unauthenticatedLogLevelFlipIsImpossible() {
    // The exact F-43 reproduction: unauthenticated POST switching a logger
    // to TRACE. Must be unroutable, not merely rejected.
    assertThat(status(HttpMethod.POST, "/admin/loggers/org.folio.servint",
        "{\"configuredLevel\": \"TRACE\"}"))
        .as("POST /admin/loggers/<name>").isEqualTo(404);
  }

  @Test
  void healthStaysServed() {
    assertThat(status(HttpMethod.GET, "/admin/health", null)).isEqualTo(200);
  }

  @Test
  void metricsStayServed() {
    // F-36's obligation: the metrics endpoint remains the deployment
    // contract for scraping cache.* meters (WidgetCacheCapacityIT covers
    // the cache meters themselves).
    assertThat(status(HttpMethod.GET, "/admin/metrics", null)).isEqualTo(200);
    assertThat(status(HttpMethod.GET, "/admin/metrics/jvm.memory.used", null)).isEqualTo(200);
  }

  private int status(HttpMethod method, String path, String jsonBody) {
    var request = RestClient.create()
        .method(method)
        .uri("http://localhost:{port}" + path, port);
    if (jsonBody != null) {
      request.contentType(MediaType.APPLICATION_JSON).body(jsonBody);
    }
    return request.exchange((req, response) -> response.getStatusCode().value());
  }
}
