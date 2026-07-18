package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * `_tenant` 2.0 lifecycle (ADR-012, REQ-020): enable creates the schema under
 * the legacy naming convention and answers 204, a repeat enable is idempotent,
 * the operation-status routes answer as declared, an Okapi-shaped disable
 * (module_from + blank module_to + purge=false) is side-effect-free (D-26),
 * a blank-module_to body without an explicit purge flag is rejected 400
 * (AC6, F-32), non-Boolean and duplicate purge discriminators are rejected
 * 400 side-effect-free (AC6, F-37), and a purge job (blank module_to +
 * purge=true) drops the schema. Review Blocker F-02 pinned: every route the
 * descriptor declares behaves as declared.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class TenantEnableIT {

  private static final String TENANT = "m2parity";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";

  @Container
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

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

  @Test
  @Order(1)
  void tenantEnableCreatesSchemaWithLegacyName() throws Exception {
    postTenant("{\"module_to\": \"mod-service-interaction-5.0.0\"}");
    assertThat(schemaExists()).isTrue();
  }

  @Test
  @Order(2)
  void repeatEnableUpgradesIdempotently() throws Exception {
    postTenant("{\"module_from\": \"mod-service-interaction-5.0.0\","
        + " \"module_to\": \"mod-service-interaction-5.0.1\"}");
    assertThat(schemaExists()).isTrue();
  }

  @Test
  @Order(3)
  void operationStatusRoutesAnswerAsDeclared() throws Exception {
    mockMvc.perform(get("/_/tenant/any-operation-id")
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", "http://localhost:9130"))
        .andExpect(status().isOk())
        .andExpect(content().string("true"));
    mockMvc.perform(delete("/_/tenant/any-operation-id")
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", "http://localhost:9130"))
        .andExpect(status().isNoContent());
  }

  @Test
  @Order(4)
  void okapiShapedDisableIsSideEffectFree() throws Exception {
    // REQ-020 AC4 / D-26: Okapi sends a disable as {module_from, blank
    // module_to, purge:false}. The module must answer 204 without Liquibase
    // and without seeding: a business row survives, the changelog row count
    // stays put, and a seeded value deleted before the disable stays deleted
    // (the upgrade path would restore it via lookupOrCreate).
    jdbcTemplate.update("INSERT INTO " + SCHEMA + ".app_setting"
        + " (st_id, st_version, st_section, st_key, st_value)"
        + " VALUES ('r15-disable-probe', 1, 'r15', 'probe', 'kept')");
    jdbcTemplate.update("DELETE FROM " + SCHEMA + ".refdata_value WHERE rdv_value = 'view'");
    var changelogRowsBefore = changelogRows();

    postTenant("{\"module_from\": \"mod-service-interaction-5.0.1\", \"purge\": false}");

    assertThat(schemaExists()).isTrue();
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA
        + ".app_setting WHERE st_id = 'r15-disable-probe'", Long.class)).isEqualTo(1);
    assertThat(changelogRows()).isEqualTo(changelogRowsBefore);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA
        + ".refdata_value WHERE rdv_value = 'view'", Long.class)).isZero();
  }

  @Test
  @Order(5)
  void omittedPurgeWithBlankModuleToIsRejected() throws Exception {
    // REQ-020 AC6 / F-32: no destructive default. A blank-module_to body
    // whose purge flag is omitted (or null) answers 400 with the errors
    // envelope and the tenant survives untouched — same probes as the
    // disable test: schema present, changelog row count unchanged.
    var changelogRowsBefore = changelogRows();

    postRejected("{\"module_from\": \"mod-service-interaction-5.0.1\"}");
    postRejected("{}");
    postRejected("{\"module_from\": \"mod-service-interaction-5.0.1\", \"purge\": null}");

    assertThat(schemaExists()).isTrue();
    assertThat(changelogRows()).isEqualTo(changelogRowsBefore);
  }

  @Test
  @Order(6)
  void nonBooleanOrDuplicatePurgeIsRejected() throws Exception {
    // REQ-020 AC6 / review-4 F-37: the purge discriminator is explicit only
    // as exactly one top-level JSON Boolean member. Coercible scalars
    // ("true", 1), duplicate members in either order, and structured values
    // must all answer 400 before binding — each shape would otherwise purge
    // (or mis-route) via Jackson coercion or last-key-wins collapse. The
    // schema and changelog prove every rejection side-effect-free.
    var changelogRowsBefore = changelogRows();

    postRejected("{\"module_from\": \"mod-service-interaction-5.0.1\", \"purge\": \"true\"}");
    postRejected("{\"module_from\": \"mod-service-interaction-5.0.1\", \"purge\": \"false\"}");
    postRejected("{\"module_from\": \"mod-service-interaction-5.0.1\", \"purge\": 1}");
    postRejected("{\"module_from\": \"mod-service-interaction-5.0.1\", \"purge\": 0}");
    postRejected("{\"purge\": false, \"purge\": true}");
    postRejected("{\"purge\": true, \"purge\": false}");
    postRejected("{\"module_to\": \"mod-service-interaction-5.0.1\", \"purge\": \"true\"}");
    postRejected("{\"module_from\": \"mod-service-interaction-5.0.1\", \"purge\": [true]}");

    // controls: a purge member nested below the top level stays invisible to
    // the discriminator (the flagless blank-module_to 400 answers), and
    // malformed JSON still takes the converter's own 400 path (D-22).
    postRejected("{\"module_from\": \"mod-service-interaction-5.0.1\","
        + " \"extra\": {\"purge\": true}}");
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", "http://localhost:9130")
            .content("{\"purge\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("malformed.json"));

    assertThat(schemaExists()).isTrue();
    assertThat(changelogRows()).isEqualTo(changelogRowsBefore);
  }

  @Test
  @Order(7)
  void purgeDropsTheSchema() throws Exception {
    postTenant("{\"module_from\": \"mod-service-interaction-5.0.1\", \"purge\": true}");
    assertThat(schemaExists()).isFalse();
  }

  private void postRejected(String body) throws Exception {
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", "http://localhost:9130")
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("purge.not.explicit"));
  }

  private void postTenant(String body) throws Exception {
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", "http://localhost:9130")
            .content(body))
        .andExpect(status().isNoContent());
  }

  private Boolean schemaExists() {
    return jdbcTemplate.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM pg_namespace WHERE nspname = '" + SCHEMA + "')",
        Boolean.class);
  }

  private Long changelogRows() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM " + SCHEMA + ".databasechangelog", Long.class);
  }
}
