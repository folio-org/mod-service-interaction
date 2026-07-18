package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * Fresh-DDL catalog regression (workstream R19): pins the R7-remediated
 * catalog claims (completion-report §fresh-DDL) on a schema created from
 * scratch by the adoption-baseline changelog — the branch where every
 * precondition misses and the createTable/addColumn DDL actually executes.
 * Each assertion names its source claim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FreshDdlCatalogIT {

  private static final String TENANT = "freshddl";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";
  private static boolean tenantInitialized;

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
  private JdbcTemplate jdbc;

  @BeforeEach
  void enableFreshTenant() throws Exception {
    if (!tenantInitialized) {
      mockMvc.perform(post("/_/tenant")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-okapi-tenant", TENANT)
              .header("x-okapi-url", "http://localhost:9130")
              .content("{\"module_to\": \"mod-service-interaction-5.0.0\"}"))
          .andExpect(status().isNoContent());
      tenantInitialized = true;
    }
  }

  @Test
  void refdataCategoryInternalHasNoColumnDefault() {
    // Review F-10 (fix R7): the port's fresh DDL had added DEFAULT false to
    // refdata_category.internal; the legacy schema declares NO default
    // (setup-refdata.groovy adds NOT NULL only). column_default must be NULL.
    var row = jdbc.queryForMap(
        "SELECT column_default, is_nullable FROM information_schema.columns"
            + " WHERE table_schema = '" + SCHEMA + "'"
            + " AND table_name = 'refdata_category' AND column_name = 'internal'");
    assertThat(row.get("column_default"))
        .as("F-10/R7: refdata_category.internal carries no DEFAULT").isNull();
    assertThat(row.get("is_nullable")).isEqualTo("NO");
  }

  @Test
  void dashboardAccessAndDisplayDataHaveNoPrimaryKey() {
    // Legacy declares NO primary key on these two tables — NOT NULL only
    // (M4-verified, adoption-baseline-dashboards.xml; completion-report
    // §fresh-DDL). pg_constraint must hold no contype='p' row for either.
    assertThat(primaryKeyCount("dashboard_access"))
        .as("dashboard_access has no primary key").isZero();
    assertThat(primaryKeyCount("dashboard_display_data"))
        .as("dashboard_display_data has no primary key").isZero();
  }

  @Test
  void legacyNamedForeignKeysExistByExactName() {
    // Completion-report §fresh-DDL: the number_generator_sequence FK names
    // are transcribed verbatim (mixed case and all) from the legacy Groovy
    // changelogs into adoption-baseline-numgen.xml, so an adopted schema and
    // a fresh one carry identical constraint names.
    assertThat(foreignKeyNames("number_generator_sequence"))
        .as("legacy-named check-digit and maximum-check FKs on number_generator_sequence")
        .contains("ngs_check_digit_algo_FK_CONSTRAINT", "ngs_ngs_maximum_check_FK_CONSTRAINT");
  }

  @Test
  void dashboardDescriptionSitsAtPinnedOrdinalFour() {
    // D-16 (wire-compat-deviations.md): the port pins dshb_description at
    // physical ordinal 4 (legacy had 5 after its later addColumn) — a benign,
    // registered deviation; the runbook's catalog diff therefore sorts by
    // column name, not ordinal. This guards the pinned ordinal from drifting.
    assertThat(jdbc.queryForObject(
        "SELECT ordinal_position FROM information_schema.columns"
            + " WHERE table_schema = '" + SCHEMA + "'"
            + " AND table_name = 'dashboard' AND column_name = 'dshb_description'",
        Integer.class))
        .as("D-16: dashboard.dshb_description pinned at ordinal 4").isEqualTo(4);
  }

  // ---------------------------------------------------------------- helpers

  private Long primaryKeyCount(String table) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM pg_constraint c"
            + " JOIN pg_class t ON c.conrelid = t.oid"
            + " JOIN pg_namespace n ON t.relnamespace = n.oid"
            + " WHERE n.nspname = '" + SCHEMA + "' AND t.relname = '" + table + "'"
            + " AND c.contype = 'p'",
        Long.class);
  }

  private List<String> foreignKeyNames(String table) {
    return jdbc.queryForList(
        "SELECT c.conname FROM pg_constraint c"
            + " JOIN pg_class t ON c.conrelid = t.oid"
            + " JOIN pg_namespace n ON t.relnamespace = n.oid"
            + " WHERE n.nspname = '" + SCHEMA + "' AND t.relname = '" + table + "'"
            + " AND c.contype = 'f'",
        String.class);
  }
}
