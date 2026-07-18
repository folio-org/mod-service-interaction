package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.yaml.snakeyaml.Yaml;

/**
 * Runtime contract gate for the tenant surface (review F-33). The six
 * generated API surfaces are compile-enforced (a controller missing a spec
 * operation fails the build), but `specs/api/servint-tenant.yaml` is
 * deliberately outside the generated set: ServintTenantController
 * hand-implements folio-spring's TenantApi (ADR-012), and a seventh generated
 * interface would fork that inheritance. This test closes the resulting gap:
 * the served `/_/tenant*` surface (Spring's handler mappings) must match the
 * spec's declared operations in both directions, and the spec's declared
 * response statuses stay pinned to the lifecycle contract TenantEnableIT
 * exercises over the wire. The spec path is overridable via the
 * `servint.tenant.spec` system property so a mutated-spec drift probe can
 * demonstrate the gate fails when the two sides diverge.
 */
@SpringBootTest
@Testcontainers
class TenantContractCompletenessIT {

  private static final String SPEC_PATH = System.getProperty(
      "servint.tenant.spec", "specs/api/servint-tenant.yaml");
  private static final Set<String> HTTP_METHODS = Set.of(
      "get", "put", "post", "delete", "options", "head", "patch", "trace");

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @Test
  void servedTenantSurfaceMatchesTheSpec() throws IOException {
    assertThat(servedOperations())
        .as("served /_/tenant* handler mappings vs %s declared operations", SPEC_PATH)
        .containsExactlyInAnyOrderElementsOf(declaredOperations().keySet());
  }

  @Test
  void declaredStatusesStayPinnedToTheLifecycleContract() throws IOException {
    // TenantEnableIT observes these statuses over the wire (204 enable and
    // disable, 400 flagless blank-module_to per REQ-020 AC6, 200 "true"
    // operation status, 204 operation delete). A spec-side status drift
    // fails here; a served-side drift fails there.
    var declared = declaredOperations();
    assertThat(declared.get("POST /_/tenant")).containsExactlyInAnyOrder("204", "400");
    assertThat(declared.get("GET /_/tenant/{operationId}")).containsExactlyInAnyOrder("200");
    assertThat(declared.get("DELETE /_/tenant/{operationId}")).containsExactlyInAnyOrder("204");
  }

  private Set<String> servedOperations() {
    var served = new TreeSet<String>();
    handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
      for (var pattern : mapping.getPathPatternsCondition().getPatterns()) {
        if (!pattern.getPatternString().startsWith("/_/tenant")) {
          continue;
        }
        for (var method : mapping.getMethodsCondition().getMethods()) {
          served.add(method.name() + " " + pattern.getPatternString());
        }
      }
    });
    return served;
  }

  /** Operation key ("POST /_/tenant") to its declared response status codes. */
  @SuppressWarnings("unchecked")
  private Map<String, Set<String>> declaredOperations() throws IOException {
    Map<String, Object> spec;
    try (InputStream in = Files.newInputStream(Path.of(SPEC_PATH))) {
      spec = new Yaml().load(in);
    }
    var declared = new TreeMap<String, Set<String>>();
    var paths = (Map<String, Map<String, Object>>) spec.get("paths");
    paths.forEach((path, pathItem) -> pathItem.forEach((key, operation) -> {
      if (!HTTP_METHODS.contains(key)) {
        return;
      }
      var responses = (Map<String, Object>) ((Map<String, Object>) operation).get("responses");
      declared.put(key.toUpperCase(Locale.ROOT) + " " + path, new TreeSet<>(responses.keySet()));
    }));
    return declared;
  }
}
