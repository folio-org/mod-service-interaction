package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * REQ-019 enable matrix: seeding follows the legacy two-bucket triggers.
 * Every enable seeds only the @Defaults baseline (DashboardAccess.Access +
 * NumberGeneratorSequence.MaximumCheck = 2 categories / 6 values, AC1); the
 * check-digit vocabulary and the 8 default generators require the literal
 * loadReference="true" (AC2/AC3); widget types require loadSample="true"
 * (AC5); repeat reference loads create nothing new and leave tenant-advanced
 * sequence state untouched (AC4). Each case enables its own tenant so the
 * counts are exact.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TenantSeedingMatrixIT {

  private static final String MODULE_TO = "\"module_to\": \"mod-service-interaction-5.0.0\"";
  private static final String NO_PARAMS = "{" + MODULE_TO + "}";
  private static final String LOAD_REFERENCE_TRUE =
      "{" + MODULE_TO + ", \"parameters\": [{\"key\": \"loadReference\", \"value\": \"true\"}]}";

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
  void noParametersSeedsOnlyTheDefaultsBaseline() throws Exception {
    postTenant("seedbase", NO_PARAMS);
    assertBaselineOnly("seedbase");
  }

  @Test
  void loadReferenceTrueSeedsCheckDigitsAndDefaultGenerators() throws Exception {
    postTenant("seedref", LOAD_REFERENCE_TRUE);
    assertReferenceLoaded("seedref");
  }

  @Test
  void loadReferenceFalseSeedsOnlyTheDefaultsBaseline() throws Exception {
    postTenant("seedreffalse",
        "{" + MODULE_TO + ", \"parameters\": [{\"key\": \"loadReference\", \"value\": \"false\"}]}");
    assertBaselineOnly("seedreffalse");
  }

  @Test
  void loadSampleTrueAddsWidgetTypesToTheBaseline() throws Exception {
    postTenant("seedsample",
        "{" + MODULE_TO + ", \"parameters\": [{\"key\": \"loadSample\", \"value\": \"true\"}]}");
    assertThat(count("seedsample", "refdata_category")).isEqualTo(2);
    assertThat(count("seedsample", "refdata_value")).isEqualTo(6);
    assertThat(count("seedsample", "number_generator")).isZero();
    assertThat(count("seedsample", "widget_type")).isGreaterThanOrEqualTo(1);
  }

  @Test
  void loadSampleFalseSeedsOnlyTheDefaultsBaseline() throws Exception {
    postTenant("seedsamplefls",
        "{" + MODULE_TO + ", \"parameters\": [{\"key\": \"loadSample\", \"value\": \"false\"}]}");
    assertBaselineOnly("seedsamplefls");
  }

  @Test
  void repeatReferenceLoadIsIdempotentAndKeepsAdvancedSequences() throws Exception {
    postTenant("seedrepeat", LOAD_REFERENCE_TRUE);
    assertReferenceLoaded("seedrepeat");

    // Tenant advances a seeded sequence; a re-enable with loadReference=true
    // must neither duplicate rows nor reset the advanced nextValue (AC4).
    var schema = schema("seedrepeat");
    jdbcTemplate.update("update " + schema + ".number_generator_sequence set ngs_next_value = 500"
        + " where ngs_code = 'requestSequence' and ngs_owner ="
        + " (select ng_id from " + schema + ".number_generator where ng_code = 'openAccess')");

    postTenant("seedrepeat", LOAD_REFERENCE_TRUE);
    assertReferenceLoaded("seedrepeat");
    assertThat(jdbcTemplate.queryForObject(
        "select ngs_next_value from " + schema + ".number_generator_sequence"
            + " where ngs_code = 'requestSequence' and ngs_owner ="
            + " (select ng_id from " + schema + ".number_generator where ng_code = 'openAccess')",
        Long.class)).isEqualTo(500L);
  }

  // ---------------------------------------------------------------- helpers

  private void postTenant(String tenant, String body) throws Exception {
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", tenant)
            .header("x-okapi-url", "http://localhost:9130")
            .content(body))
        .andExpect(status().isNoContent());
  }

  private void assertBaselineOnly(String tenant) {
    assertThat(count(tenant, "refdata_category")).isEqualTo(2);
    assertThat(count(tenant, "refdata_value")).isEqualTo(6);
    assertThat(count(tenant, "number_generator")).isZero();
    assertThat(count(tenant, "widget_type")).isZero();
  }

  private void assertReferenceLoaded(String tenant) {
    assertThat(count(tenant, "refdata_category")).isEqualTo(3);
    assertThat(count(tenant, "refdata_value")).isEqualTo(13);
    assertThat(count(tenant, "number_generator")).isEqualTo(8);
    assertThat(count(tenant, "widget_type")).isZero();
  }

  private Long count(String tenant, String table) {
    return jdbcTemplate.queryForObject(
        "select count(*) from " + schema(tenant) + "." + table, Long.class);
  }

  private String schema(String tenant) {
    return tenant + "_mod_service_interaction";
  }
}
