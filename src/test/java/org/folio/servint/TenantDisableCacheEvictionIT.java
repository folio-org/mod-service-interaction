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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jwt.SignedJWT;
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
 * Behavioral eviction proof for the `_tenant` 2.0 disable path (workstream
 * R38, review-4 F-45: the disable-time eviction of BOTH module-level
 * per-tenant caches was previously untested). Each cache is first warmed
 * with real traffic, then shown — as a control — to keep serving its cached
 * answer while the backing state changes underneath it, and only then is the
 * tenant disabled ({@code purge:false}): the next call must observably
 * re-load, not re-serve.
 *
 * <ul>
 *   <li>Widget-definition tenant cache: the stubbed upstream definitions
 *       change while the implementor set stays constant, so a warm cache is
 *       provably blind to the change; after disable the listing must show
 *       the new harvest (and WireMock must see a second harvest call).</li>
 *   <li>Attestation signing-key cache: the backing db_key_pair row is
 *       deleted, so a warm cache provably keeps signing with the deleted
 *       key; after disable the next token must be signed by a freshly
 *       created key (new kid, new db row).</li>
 * </ul>
 *
 * <p>The disable itself must leave the schema and its data untouched
 * (ADR-012 / D-26) — asserted alongside the eviction proofs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantDisableCacheEvictionIT {

  private static final String TENANT = "evictdis";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";
  private static final String USER_ID = "3b1c6f0d-2a75-4c1e-9d64-58f14c2a90b7";

  private static boolean initialized;
  private static String kidBeforeDisable;

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
    postTenant("{\"module_to\": \"mod-service-interaction-5.0.0\"}");
    jdbcTemplate.update("insert into " + SCHEMA + ".widget_type "
            + "(wtype_id, wtype_version, wtype_name, wtype_type_version, wtype_schema) "
            + "values (?, 0, 'TestType', '1.0', '{\"type\":\"object\"}')",
        UUID.randomUUID().toString());
    OKAPI.stubFor(WireMock.get(urlEqualTo("/_/proxy/tenants/" + TENANT + "/interfaces"))
        .willReturn(okJson("[{\"id\":\"dashboard\",\"version\":\"1.0\"}]")));
    OKAPI.stubFor(WireMock.get(urlPathEqualTo("/_/proxy/tenants/" + TENANT + "/modules"))
        .withQueryParam("provide", equalTo("dashboard"))
        .willReturn(okJson("[{\"id\":\"mod-shared-1.0.0\"}]")));
    stubDefinitions(def("Alpha", "1.0"));
    initialized = true;
  }

  // ---------------------------------------------------------------- helpers

  private void postTenant(String body) throws Exception {
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", OKAPI.baseUrl())
            .content(body))
        .andExpect(status().isNoContent());
  }

  private static void stubDefinitions(String definition) {
    OKAPI.stubFor(WireMock.get(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Tenant", equalTo(TENANT))
        .withHeader("X-Okapi-Module-Id", equalTo("mod-shared-1.0.0"))
        .willReturn(okJson("[" + definition + "]")));
  }

  private static String def(String name, String version) {
    return "{\"name\":\"" + name + "\",\"version\":\"" + version + "\","
        + "\"type\":{\"name\":\"TestType\",\"version\":\"1.0\"},"
        + "\"definition\":{\"anything\":true}}";
  }

  private List<String> globalNames() throws Exception {
    var result = mockMvc.perform(get("/servint/widgets/definitions/global")
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", OKAPI.baseUrl()))
        .andExpect(status().isOk()).andReturn();
    var names = new ArrayList<String>();
    json.readTree(result.getResponse().getContentAsString())
        .forEach(node -> names.add(node.path("name").asText()));
    return names;
  }

  private String tokenKid() throws Exception {
    var result = mockMvc.perform(get("/servint/attestation/token")
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-user-id", USER_ID))
        .andExpect(status().isOk()).andReturn();
    var body = json.readTree(result.getResponse().getContentAsString());
    return SignedJWT.parse(body.path("token").asText()).getHeader().getKeyID();
  }

  private void verifyHarvestCount(int count) {
    OKAPI.verify(count, getRequestedFor(urlEqualTo("/dashboard/definitions"))
        .withHeader("X-Okapi-Tenant", equalTo(TENANT)));
  }

  // ------------------------------------------------------------------ tests

  @Test
  @Order(1)
  void realTrafficWarmsBothCaches() throws Exception {
    assertThat(globalNames()).containsExactly("Alpha");
    verifyHarvestCount(1);

    kidBeforeDisable = tokenKid();
    assertThat(jdbcTemplate.queryForList("select kp_id from " + SCHEMA + ".db_key_pair",
        String.class)).containsExactly(kidBeforeDisable);
  }

  @Test
  @Order(2)
  void warmCachesAreBlindToBackingStateChanges() throws Exception {
    // Control leg: the probes must be able to DISTINGUISH a cache hit from a
    // re-load, or the eviction assertions in order 3 would be vacuous.
    // Upstream now serves AlphaPrime (implementor set unchanged) and the
    // signing key's db row is gone — yet both caches keep answering warm.
    stubDefinitions(def("AlphaPrime", "2.0"));
    jdbcTemplate.update("delete from " + SCHEMA + ".db_key_pair");

    assertThat(globalNames()).as("warm widget cache serves the stale harvest").containsExactly("Alpha");
    verifyHarvestCount(1);
    assertThat(tokenKid()).as("warm key cache signs with the deleted key").isEqualTo(kidBeforeDisable);
  }

  @Test
  @Order(3)
  void disableEvictsBothCachesBehaviorally() throws Exception {
    // `_tenant` 2.0 disable: blank module_to + explicit purge:false (D-26).
    postTenant("{\"module_from\": \"mod-service-interaction-5.0.0\", \"purge\": false}");

    // Disable leaves the schema and its data untouched.
    assertThat(jdbcTemplate.queryForObject(
        "SELECT to_regclass('" + SCHEMA + ".widget_type') IS NOT NULL", Boolean.class))
        .as("schema survives the disable").isTrue();
    assertThat(jdbcTemplate.queryForObject(
        "SELECT count(*) FROM " + SCHEMA + ".widget_type", Long.class)).isEqualTo(1L);

    // Widget cache: the next listing re-harvests (second upstream call) and
    // sees the changed upstream the warm cache was blind to.
    assertThat(globalNames()).as("post-disable listing shows the fresh harvest")
        .containsExactly("AlphaPrime");
    verifyHarvestCount(2);

    // Key cache: the next token cannot be signed by the deleted key — the
    // service re-loads, finds no row, and creates a fresh key pair.
    var kidAfterDisable = tokenKid();
    assertThat(kidAfterDisable).as("post-disable token uses a freshly created key")
        .isNotEqualTo(kidBeforeDisable);
    assertThat(jdbcTemplate.queryForList("select kp_id from " + SCHEMA + ".db_key_pair",
        String.class)).containsExactly(kidAfterDisable);
  }
}
