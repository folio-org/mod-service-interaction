package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
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
import org.testcontainers.utility.MountableFile;

/**
 * Populated-adoption regression (workstream R19, review follow-up to F-10 /
 * ADR-005): restores the REAL legacy module's populated tenant dump
 * ({@code docs/migration/evidence/r13-legacy/r13b-populated-schema.sql},
 * schema {@code r13b_mod_service_interaction}, row-count ground truth in
 * {@code run2/rowcounts.tsv}) into the suite's Postgres and then runs the
 * port's `_tenant` upgrade against it. Asserts the adoption model end to end:
 * every adopt-* changeset MARK_RANs, no legacy table is dropped or added
 * (except Liquibase's own bookkeeping pair), the row counts of ALL 37 legacy
 * tables survive unchanged against the full r13b ground-truth fixture (the
 * whole rowcounts.tsv, not a sample — workstream R34, review-4 F-41), legacy
 * rows read back over the wire, two-bucket re-seeding is idempotent, and a
 * repeat upgrade is a no-op. (Bit-for-bit row-content identity of every
 * column is NOT asserted — the honest residual recorded in TRC-023.)
 *
 * <p>The container is per-class, so the restored fixture cannot leak into
 * other suites.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class AdoptedSchemaUpgradeIT {

  /** Tenant MUST be r13b: the dump's schema name is r13b_mod_service_interaction. */
  private static final String TENANT = "r13b";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";

  private static final Path FIXTURE =
      Path.of("docs/migration/evidence/r13-legacy/r13b-populated-schema.sql").toAbsolutePath();

  private static final String UPGRADE_BODY =
      "{\"module_from\": \"mod-service-interaction-4.4.0\","
          + " \"module_to\": \"mod-service-interaction-5.0.0\","
          + " \"parameters\": [{\"key\": \"loadReference\", \"value\": \"true\"}]}";

  /**
   * Every adoption changeset in changes/adoption-baseline*.xml (the master
   * changelog contains nothing else). On the r13b fixture every precondition
   * matches: all 14 guarded tables exist and app_setting.st_hidden exists,
   * so all 14 must MARK_RAN.
   */
  private static final Set<String> ADOPTION_CHANGESET_IDS = Set.of(
      // adoption-baseline-numgen.xml
      "adopt-refdata-category", "adopt-refdata-value",
      "adopt-number-generator", "adopt-number-generator-sequence",
      // adoption-baseline-dashboards.xml
      "adopt-external-user", "adopt-dashboard", "adopt-widget-type",
      "adopt-widget-definition", "adopt-widget-instance",
      "adopt-dashboard-access", "adopt-dashboard-display-data",
      // adoption-baseline-keypair.xml
      "adopt-db-key-pair",
      // adoption-baseline-settings.xml
      "adopt-app-setting", "adopt-app-setting-hidden");

  /**
   * Ground-truth row counts captured from the real legacy rig — the FULL
   * 37-table fixture, parsed and compared table by table (R34, review-4
   * F-41: the continuity comparison must cover the whole census, not the
   * former 6-entry sample).
   */
  private static final Path ROWCOUNTS =
      Path.of("docs/migration/evidence/r13-legacy/run2/rowcounts.tsv").toAbsolutePath();

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

  private final ObjectMapper json = new ObjectMapper();

  // Snapshot shared across the ordered test methods (new instance per method).
  private static String restorePath;
  private static Set<String> preUpgradeTables;
  private static final Map<String, Long> preUpgradeRows = new HashMap<>();

  @Test
  @Order(1)
  void restoreLegacyFixtureAndSnapshot() throws Exception {
    // The dump carries "ALTER ... OWNER TO folio_admin" (39 statements) from
    // the real legacy rig; the role must exist or ON_ERROR_STOP aborts.
    var createRole = POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(),
        "-d", POSTGRES.getDatabaseName(), "-c", "CREATE ROLE folio_admin");
    assertThat(createRole.getExitCode())
        .as("CREATE ROLE folio_admin: %s", createRole.getStderr())
        .isZero();

    // JDBC cannot execute a plain-format dump (COPY ... FROM stdin blocks),
    // so the file is run with psql inside the container.
    POSTGRES.copyFileToContainer(MountableFile.forHostPath(FIXTURE), "/tmp/r13b.sql");
    var restore = runPsqlFile("/tmp/r13b.sql");

    if (restore.getExitCode() == 0) {
      restorePath = "verbatim (pg_dump 18 preamble accepted as-is)";
    } else {
      // The dump was taken with pg_dump 18 against postgres:18; restoring
      // into this suite's postgres:16 can trip on two preamble artifacts:
      // the \restrict psql meta-command (unknown before psql 16.10) and
      // "SET transaction_timeout" (a PG17+ GUC PG16 rejects under
      // ON_ERROR_STOP). Both only affect the restore session — no schema
      // effect — so strip them and rerun.
      var firstError = restore.getStderr();
      var stripped = Files.createTempFile("r13b-pg16-compat", ".sql");
      try {
        Files.write(stripped, Files.readAllLines(FIXTURE, StandardCharsets.UTF_8).stream()
            .filter(line -> !line.startsWith("\\restrict") && !line.startsWith("\\unrestrict")
                && !line.startsWith("SET transaction_timeout"))
            .collect(Collectors.toList()), StandardCharsets.UTF_8);
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(stripped), "/tmp/r13b-pg16.sql");
      } finally {
        Files.deleteIfExists(stripped);
      }
      restore = runPsqlFile("/tmp/r13b-pg16.sql");
      restorePath = "pg16-compat strip (removed restrict/unrestrict meta-commands and"
          + " SET transaction_timeout) after verbatim attempt failed with: "
          + firstError.strip();
    }
    assertThat(restore.getExitCode())
        .as("fixture restore failed; stderr: %s", restore.getStderr())
        .isZero();
    System.out.println("[AdoptedSchemaUpgradeIT] fixture restore path: " + restorePath);

    // Pre-upgrade snapshot: the table-name census and the row counts of all
    // 37 tables, both compared against the committed r13b ground-truth
    // fixture (run2/rowcounts.tsv) — the whole census, not a sample.
    var groundTruth = groundTruthRows();
    assertThat(groundTruth).as("ground-truth fixture covers the full 37-table census").hasSize(37);
    preUpgradeTables = tableNames();
    assertThat(preUpgradeTables)
        .as("legacy dump table census matches the ground-truth fixture")
        .containsExactlyInAnyOrderElementsOf(groundTruth.keySet());
    groundTruth.forEach((table, expected) -> {
      long count = rowCount(table);
      assertThat(count).as("pre-upgrade %s rows", table).isEqualTo(expected);
      preUpgradeRows.put(table, count);
    });
  }

  @Test
  @Order(2)
  void upgradeFromLegacyAnswers204() throws Exception {
    postTenant(UPGRADE_BODY);
  }

  @Test
  @Order(3)
  void adoptionChangesetsAllMarkRan() {
    // 4a. The port ignores the legacy tenant_changelog and writes its own
    // databasechangelog. The master changelog consists of exactly the 14
    // adopt-* changesets (there are no non-adoption changesets yet), and on
    // this fully populated legacy schema every precondition matched — so the
    // observed structure is: 14 rows, all id LIKE 'adopt-%', all MARK_RAN,
    // zero EXECUTED.
    assertThat(jdbc.queryForObject(
        "SELECT to_regclass('" + SCHEMA + ".databasechangelog') IS NOT NULL", Boolean.class))
        .as("databasechangelog exists in the adopted schema").isTrue();

    var markRanIds = new TreeSet<>(jdbc.queryForList(
        "SELECT id FROM " + SCHEMA + ".databasechangelog WHERE exectype = 'MARK_RAN'",
        String.class));
    assertThat(markRanIds)
        .as("every adoption changeset whose precondition matched an existing table/column MARK_RANs")
        .containsExactlyInAnyOrderElementsOf(ADOPTION_CHANGESET_IDS);

    var byExectype = new HashMap<String, Long>();
    jdbc.queryForList("SELECT exectype, count(*) AS n FROM " + SCHEMA
            + ".databasechangelog GROUP BY exectype")
        .forEach(row -> byExectype.put((String) row.get("exectype"), (Long) row.get("n")));
    assertThat(byExectype)
        .as("changelog holds the adoption changesets only: all MARK_RAN, none EXECUTED")
        .containsOnlyKeys("MARK_RAN");
    assertThat(byExectype.get("MARK_RAN")).isEqualTo((long) ADOPTION_CHANGESET_IDS.size());
  }

  @Test
  @Order(4)
  void catalogUnchangedExceptLiquibaseBookkeeping() {
    // 4b. Catalog diff per the cutover runbook convention: the only expected
    // additions are Liquibase's own databasechangelog + databasechangeloglock;
    // nothing legacy may be dropped (custom_property_* residue and the legacy
    // tenant_changelog pair stay untouched — dossier D-16).
    var postUpgradeTables = tableNames();
    var additions = new TreeSet<>(postUpgradeTables);
    additions.removeAll(preUpgradeTables);
    assertThat(additions)
        .as("only the Liquibase bookkeeping tables are added")
        .containsExactly("databasechangelog", "databasechangeloglock");
    assertThat(postUpgradeTables)
        .as("no legacy table dropped")
        .containsAll(preUpgradeTables);
    assertThat(postUpgradeTables).hasSize(preUpgradeTables.size() + 2);
    // Spot-check the deliberately untouched residue by name.
    assertThat(postUpgradeTables).contains(
        "custom_property", "custom_property_definition", "tenant_changelog", "tenant_changelog_lock");
  }

  @Test
  @Order(5)
  void businessRowCountsSurviveTheUpgrade() {
    // 4c. No data loss, across all 37 legacy tables: the adopted rows are
    // untouched, and re-seeding on this schema creates nothing new (the
    // legacy 9 generators already include the 8 seeded defaults; the legacy
    // 15 refdata values already include all 13 the port would seed).
    preUpgradeRows.forEach((table, before) ->
        assertThat(rowCount(table)).as("post-upgrade %s rows", table).isEqualTo(before));
  }

  @Test
  @Order(6)
  void legacyRowsReadBackOverTheWire() throws Exception {
    // 4d. Wire readback: the legacy-created generator m4gen must appear in the
    // listing next to the >= 9 adopted rows, and the widget-definition listing
    // answers 200 on the adopted schema.
    var result = mockMvc.perform(get("/servint/numberGenerators")
            .param("perPage", "100")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andReturn();
    var generators = json.readTree(result.getResponse().getContentAsString());
    assertThat(generators.isArray()).as("bare-array listing").isTrue();
    var codes = new ArrayList<String>();
    generators.forEach(node -> codes.add(node.path("code").asText()));
    assertThat(codes).hasSizeGreaterThanOrEqualTo(9).contains("m4gen");

    mockMvc.perform(get("/servint/widgets/definitions")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk());
  }

  @Test
  @Order(7)
  void reSeedingIsIdempotentOnTheAdoptedSchema() {
    // 4e. Two-bucket seeding (REQ-019) on an adopted schema tops up via
    // lookupOrCreate — it must never duplicate what legacy already seeded.
    assertThat(jdbc.queryForObject("SELECT count(*) - count(DISTINCT ng_code) FROM "
        + SCHEMA + ".number_generator", Long.class))
        .as("number_generator codes stay unique after re-seeding").isZero();

    assertThat(jdbc.queryForList("SELECT rdv_owner, rdv_value FROM " + SCHEMA
        + ".refdata_value GROUP BY rdv_owner, rdv_value HAVING count(*) > 1"))
        .as("no duplicated refdata (owner category, value) pairs").isEmpty();
    assertThat(rowCount("refdata_value"))
        .as("refdata vocabularies topped up, never trimmed")
        .isGreaterThanOrEqualTo(preUpgradeRows.get("refdata_value"));
  }

  @Test
  @Order(8)
  void repeatUpgradeIsIdempotent() throws Exception {
    // 5. A second identical upgrade answers 204 and leaves the changelog
    // untouched (all changesets already ran or MARK_RANned).
    var changelogRowsBefore = jdbc.queryForObject(
        "SELECT count(*) FROM " + SCHEMA + ".databasechangelog", Long.class);

    postTenant(UPGRADE_BODY);

    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM " + SCHEMA + ".databasechangelog", Long.class))
        .as("repeat upgrade adds no changelog rows").isEqualTo(changelogRowsBefore);
    businessRowCountsSurviveTheUpgrade();
  }

  // ---------------------------------------------------------------- helpers

  /** Parses the committed rowcounts.tsv fixture: one {@code table\tcount} line per table. */
  private static Map<String, Long> groundTruthRows() throws Exception {
    var rows = new TreeMap<String, Long>();
    for (String line : Files.readAllLines(ROWCOUNTS, StandardCharsets.UTF_8)) {
      if (line.isBlank()) {
        continue;
      }
      var fields = line.split("\t");
      rows.put(fields[0], Long.parseLong(fields[1]));
    }
    return rows;
  }

  private org.testcontainers.containers.Container.ExecResult runPsqlFile(String containerPath)
      throws Exception {
    return POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(),
        "-d", POSTGRES.getDatabaseName(), "-v", "ON_ERROR_STOP=1", "-f", containerPath);
  }

  private void postTenant(String body) throws Exception {
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", "http://localhost:9130")
            .content(body))
        .andExpect(status().isNoContent());
  }

  private Set<String> tableNames() {
    return new HashSet<>(jdbc.queryForList(
        "SELECT table_name FROM information_schema.tables"
            + " WHERE table_schema = '" + SCHEMA + "' AND table_type = 'BASE TABLE'",
        String.class));
  }

  private long rowCount(String table) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM " + SCHEMA + "." + table, Long.class);
    return count == null ? -1 : count;
  }
}
