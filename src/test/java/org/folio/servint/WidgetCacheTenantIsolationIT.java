package org.folio.servint;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Regression for review finding F-07 (R8a): the widget-definition harvest
 * cache must be tenant-keyed. Reproduces the review scenario — two tenants
 * exposing the SAME dashboard-implementor module ids but serving different
 * definitions — and asserts one tenant's listing never answers from the
 * other tenant's cached harvest. Before the fix the second tenant saw an
 * unchanged implementor set plus a non-empty singleton cache and served
 * tenant A's definitions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WidgetCacheTenantIsolationIT {

  private static final String TENANT_A = "cacheisoa";
  private static final String TENANT_B = "cacheisob";
  private static boolean initialized;

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
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private final ObjectMapper json = new ObjectMapper();

  @AfterAll
  static void stopOkapi() {
    OKAPI.stop();
  }

  @BeforeEach
  void setUp() throws Exception {
    if (initialized) {
      return;
    }
    OKAPI.start();
    for (var tenant : new String[] {TENANT_A, TENANT_B}) {
      mockMvc.perform(post("/_/tenant")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-okapi-tenant", tenant)
              .header("x-okapi-url", OKAPI.baseUrl())
              .content("{\"module_to\": \"mod-service-interaction-5.0.0\"}"))
          .andExpect(status().is2xxSuccessful());
      jdbcTemplate.update("insert into " + tenant + "_mod_service_interaction.widget_type "
              + "(wtype_id, wtype_version, wtype_name, wtype_type_version, wtype_schema) "
              + "values (?, 0, 'TestType', '1.0', '{\"type\":\"object\"}')",
          UUID.randomUUID().toString());
      stubOkapiFor(tenant);
    }
    // The SAME module id implements dashboard 1.0 in both tenants, but each
    // tenant's harvest returns a different definition.
    stubDefinitions(TENANT_A, def("Alpha", "1.0"));
    stubDefinitions(TENANT_B, def("Beta", "1.0"));
    initialized = true;
  }

  private static void stubOkapiFor(String tenant) {
    OKAPI.stubFor(WireMock.get(urlEqualTo("/_/proxy/tenants/" + tenant + "/interfaces"))
        .willReturn(okJson("[{\"id\":\"dashboard\",\"version\":\"1.0\"}]")));
    OKAPI.stubFor(WireMock.get(urlPathEqualTo("/_/proxy/tenants/" + tenant + "/modules"))
        .withQueryParam("provide", equalTo("dashboard"))
        .willReturn(okJson("[{\"id\":\"mod-shared-1.0.0\"}]")));
  }

  private static void stubDefinitions(String tenant, String definition) {
    OKAPI.stubFor(WireMock.get(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Tenant", equalTo(tenant))
        .withHeader("X-Okapi-Module-Id", equalTo("mod-shared-1.0.0"))
        .willReturn(okJson("[" + definition + "]")));
  }

  private static String def(String name, String version) {
    return "{\"name\":\"" + name + "\",\"version\":\"" + version + "\","
        + "\"type\":{\"name\":\"TestType\",\"version\":\"1.0\"},"
        + "\"definition\":{\"anything\":true}}";
  }

  private List<String> globalNames(String tenant) throws Exception {
    var result = mockMvc.perform(get("/servint/widgets/definitions/global")
            .header("x-okapi-tenant", tenant)
            .header("x-okapi-url", OKAPI.baseUrl()))
        .andExpect(status().isOk()).andReturn();
    JsonNode array = json.readTree(result.getResponse().getContentAsString());
    var names = new ArrayList<String>();
    array.forEach(node -> names.add(node.path("name").asText()));
    return names;
  }

  @Test
  @Order(1)
  void eachTenantHarvestsItsOwnDefinitions() throws Exception {
    assertThat(globalNames(TENANT_A)).containsExactly("Alpha");
    // Review reproduction: with the singleton cache this answered ["Alpha"]
    // because tenant B's implementor set matched the cached one.
    assertThat(globalNames(TENANT_B)).containsExactly("Beta");
  }

  @Test
  @Order(2)
  void cachedAnswersStayTenantScoped() throws Exception {
    assertThat(globalNames(TENANT_A)).containsExactly("Alpha");
    assertThat(globalNames(TENANT_B)).containsExactly("Beta");
    // Four /global calls total, but each tenant harvested exactly once —
    // the repeat calls answered from that tenant's own cache entry.
    OKAPI.verify(1, getRequestedFor(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Tenant", equalTo(TENANT_A)));
    OKAPI.verify(1, getRequestedFor(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Tenant", equalTo(TENANT_B)));
  }

  @Test
  @Order(3)
  void tenantUpgradeEvictsTheTenantsCacheOnly() throws Exception {
    // Serve a new definition to tenant B and run an upgrade job for it: the
    // lifecycle eviction must force a fresh harvest for B while A keeps
    // answering from its untouched cache entry.
    stubDefinitions(TENANT_B, def("BetaPrime", "2.0"));
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT_B)
            .header("x-okapi-url", OKAPI.baseUrl())
            .content("{\"module_from\": \"mod-service-interaction-5.0.0\","
                + " \"module_to\": \"mod-service-interaction-5.0.1\"}"))
        .andExpect(status().is2xxSuccessful());

    assertThat(globalNames(TENANT_B)).containsExactly("BetaPrime");
    assertThat(globalNames(TENANT_A)).containsExactly("Alpha");
    OKAPI.verify(1, getRequestedFor(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Tenant", equalTo(TENANT_A)));
    OKAPI.verify(2, getRequestedFor(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Tenant", equalTo(TENANT_B)));
  }
}
