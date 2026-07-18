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
 * Behavior-parity suite for widget-definition federation (dossier §5.4):
 * Okapi discovery of dashboard-interface implementors, per-module harvest
 * addressed by X-Okapi-Module-Id, generic + type schema validation,
 * first-wins name de-duplication, non-list discard, module-level caching,
 * and highest-compatible-minor-per-major selection with name/nameLike/
 * version filters. A WireMock server plays Okapi and both implementors.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WidgetFederationParityIT {

  private static final String TENANT = "fedparity";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";
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
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", OKAPI.baseUrl())
            .content("{\"module_to\": \"mod-service-interaction-5.0.0\"}"))
        .andExpect(status().is2xxSuccessful());

    // A permissive local WidgetType the harvested definitions target
    // (types have no POST route; legacy gets them from sample data).
    jdbcTemplate.update("insert into " + SCHEMA + ".widget_type "
            + "(wtype_id, wtype_version, wtype_name, wtype_type_version, wtype_schema) "
            + "values (?, 0, 'TestType', '1.0', '{\"type\":\"object\"}')",
        UUID.randomUUID().toString());

    stubOkapi();
    initialized = true;
  }

  private void stubOkapi() {
    OKAPI.stubFor(WireMock.get(urlEqualTo("/_/proxy/tenants/" + TENANT + "/interfaces"))
        .willReturn(okJson("[{\"id\":\"dashboard\",\"version\":\"1.0\"},{\"id\":\"servint\",\"version\":\"4.4\"}]")));
    OKAPI.stubFor(WireMock.get(urlPathEqualTo("/_/proxy/tenants/" + TENANT + "/modules"))
        .withQueryParam("provide", equalTo("dashboard"))
        .willReturn(okJson("[{\"id\":\"mod-a-1.0.0\"},{\"id\":\"mod-b-1.0.0\"},{\"id\":\"mod-c-1.0.0\"}]")));

    // mod-a: ERM 1.0 + 1.3 (same major -> highest minor wins), Reports 2.1
    // (other major, kept), one definition failing the generic schema, and one
    // whose type has no compatible local WidgetType.
    OKAPI.stubFor(WireMock.get(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Module-Id", equalTo("mod-a-1.0.0"))
        .willReturn(okJson(defs(
            def("ERM Agreements", "1.0", "TestType", "1.0"),
            def("ERM Agreements", "1.3", "TestType", "1.0"),
            def("Reports", "2.1", "TestType", "1.0"),
            "{\"name\":\"Broken\",\"version\":\"1.0\"}",
            def("Orphan", "1.0", "NoSuchType", "1.0")))));

    // mod-b: a same-name conflict (dropped, first module wins) + Other 1.0.
    OKAPI.stubFor(WireMock.get(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Module-Id", equalTo("mod-b-1.0.0"))
        .willReturn(okJson(defs(
            def("ERM Agreements", "1.1", "TestType", "1.0"),
            def("Other", "1.0", "TestType", "1.0")))));

    // mod-c: a non-list response, discarded whole.
    OKAPI.stubFor(WireMock.get(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Module-Id", equalTo("mod-c-1.0.0"))
        .willReturn(okJson("{\"unexpected\":\"object\"}")));
  }

  private static String def(String name, String version, String typeName, String typeVersion) {
    return "{\"name\":\"" + name + "\",\"version\":\"" + version + "\","
        + "\"type\":{\"name\":\"" + typeName + "\",\"version\":\"" + typeVersion + "\"},"
        + "\"definition\":{\"anything\":true}}";
  }

  private static String defs(String... definitions) {
    return "[" + String.join(",", definitions) + "]";
  }

  private JsonNode global(String... queryPairs) throws Exception {
    var request = get("/servint/widgets/definitions/global")
        .header("x-okapi-tenant", TENANT)
        .header("x-okapi-url", OKAPI.baseUrl());
    for (var i = 0; i < queryPairs.length; i += 2) {
      request = request.queryParam(queryPairs[i], queryPairs[i + 1]);
    }
    var result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private List<String> nameVersions(JsonNode array) {
    var pairs = new ArrayList<String>();
    array.forEach(node -> pairs.add(node.path("name").asText() + "@" + node.path("version").asText()));
    return pairs;
  }

  @Test
  @Order(1)
  void federatedHarvestValidatesDeduplicatesAndSelectsVersions() throws Exception {
    var result = global();
    assertThat(nameVersions(result)).containsExactlyInAnyOrder(
        "ERM Agreements@1.3", "Reports@2.1", "Other@1.0");
    for (var definition : result) {
      assertThat(definition.path("type").path("name").asText()).isEqualTo("TestType");
      assertThat(definition.path("definition").path("anything").asBoolean()).isTrue();
    }
  }

  @Test
  @Order(2)
  void filtersApplyNameNameLikeAndVersionCompatibility() throws Exception {
    assertThat(nameVersions(global("name", "erm agreements")))
        .containsExactly("ERM Agreements@1.3");
    assertThat(nameVersions(global("nameLike", "^oth"))).containsExactly("Other@1.0");
    // version 1.0: same-MAJOR compatible definitions only — Reports (2.x) drops.
    assertThat(nameVersions(global("version", "1.0")))
        .containsExactlyInAnyOrder("ERM Agreements@1.3", "Other@1.0");
    assertThat(nameVersions(global("version", "2.0"))).containsExactly("Reports@2.1");
  }

  @Test
  @Order(3)
  void harvestIsCachedAtModuleLevel() {
    // Five /global calls in the tests above, but each implementor was
    // harvested exactly once — the cache absorbed the rest.
    OKAPI.verify(3, getRequestedFor(urlEqualTo("/dashboard/definitions")));
  }
}
