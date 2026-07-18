package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Behavior-parity corpus for the numgen slice: every expected output below is
 * a pinned fixture from the legacy NumberGeneratorSpec (dossier §6). If one
 * of these fails, the Java module and the Grails module disagree on the wire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NumberGeneratorParityIT {

  private static final String TENANT = "ngparity";
  private static final String YEAR = String.valueOf(LocalDate.now(ZoneOffset.UTC).getYear());
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
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void enableTenant() throws Exception {
    if (!tenantInitialized) {
      mockMvc.perform(post("/_/tenant")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-okapi-tenant", TENANT)
              .header("x-okapi-url", "http://localhost:9130")
              .content("{\"module_to\": \"mod-service-interaction-5.0.0\","
                  + " \"parameters\": [{\"key\": \"loadReference\", \"value\": \"true\"}]}"))
          .andExpect(status().is2xxSuccessful());
      tenantInitialized = true;
    }
  }

  // ---------------------------------------------------------------- helpers

  private JsonNode next(String generator, String sequence) throws Exception {
    MvcResult result = mockMvc.perform(get("/servint/numberGenerators/getNextNumber")
            .param("generator", generator)
            .param("sequence", sequence)
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private String nextValue(String generator, String sequence) throws Exception {
    var node = next(generator, sequence);
    assertThat(node.path("status").asText()).isIn("OK", "WARNING");
    return node.path("nextValue").asText();
  }

  private String createGenerator(String code) throws Exception {
    var body = json.createObjectNode().put("code", code).put("name", code);
    MvcResult result = mockMvc.perform(post("/servint/numberGenerators")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content(body.toString()))
        .andExpect(status().isCreated())
        .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private ObjectNode seq(String generatorId, String code) {
    var node = json.createObjectNode();
    node.putObject("owner").put("id", generatorId);
    node.put("code", code).put("name", code);
    return node;
  }

  private ObjectNode withAlgo(ObjectNode node, String algoValue) {
    node.putObject("checkDigitAlgo").put("value", algoValue);
    return node;
  }

  private String createSequence(ObjectNode body) throws Exception {
    MvcResult result = mockMvc.perform(post("/servint/numberGeneratorSequences")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content(body.toString()))
        .andExpect(status().isCreated())
        .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private JsonNode getSequence(String id) throws Exception {
    MvcResult result = mockMvc.perform(get("/servint/numberGeneratorSequences/" + id)
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  // ---------------------------------------------------------------- fixtures

  @Test
  @Order(1)
  void defaultTemplateFixtures() throws Exception {
    var gen = createGenerator("fixtures1");
    createSequence(seq(gen, "patron").put("prefix", "user").put("format", "000000000"));
    createSequence(seq(gen, "staff").put("prefix", "staff").put("postfix", "test").put("format", "000,000,000"));
    createSequence(seq(gen, "noformat").put("prefix", "nf"));
    createSequence(seq(gen, "highinit").put("prefix", "hi").put("format", "000000000").put("nextValue", 100000));

    assertThat(nextValue("fixtures1", "patron")).isEqualTo("user-000000001");
    assertThat(nextValue("fixtures1", "patron")).isEqualTo("user-000000002");
    assertThat(nextValue("fixtures1", "patron")).isEqualTo("user-000000003");
    assertThat(nextValue("fixtures1", "staff")).isEqualTo("staff-000,000,001-test");
    assertThat(nextValue("fixtures1", "noformat")).isEqualTo("nf-1");
    assertThat(nextValue("fixtures1", "highinit")).isEqualTo("hi-000100000");
  }

  @Test
  @Order(2)
  void checkDigitDefaultTemplateFixtures() throws Exception {
    var gen = createGenerator("fixtures2");
    createSequence(withAlgo(seq(gen, "mod11test").put("format", "000000000").put("nextValue", 100000),
        "isbn10checkdigit"));
    createSequence(withAlgo(seq(gen, "e069").put("prefix", "069").put("postfix", "1").put("format", "000000000"),
        "ean13"));

    assertThat(nextValue("fixtures2", "mod11test")).isEqualTo("000100000-4");
    assertThat(nextValue("fixtures2", "e069")).isEqualTo("069-000000001-1-7");
  }

  @Test
  @Order(3)
  void customTemplateFixtures() throws Exception {
    var gen = createGenerator("fixtures3");
    createSequence(withAlgo(seq(gen, "t0698").put("format", "000000000")
        .put("outputTemplate", "0698${generated_number}${checksum}"), "ean13"));
    createSequence(withAlgo(seq(gen, "t0699").put("format", "000000000")
        .put("outputTemplate", "0699-${generated_number}-${checksum}-post"), "ean13"));
    createSequence(withAlgo(seq(gen, "t0700").put("format", "000000000")
        .put("outputTemplate",
            "0700-${generated_number.substring(0,4)}-${checksum}-${generated_number.substring(4,9)}-post"),
        "ean13"));
    createSequence(withAlgo(seq(gen, "t0800").put("format", "000000000")
        .put("preChecksumTemplate", "100${generated_number}001")
        .put("outputTemplate", "0700-${checksum_input_template}-${checksum}-post"), "ean13"));

    assertThat(nextValue("fixtures3", "t0698")).isEqualTo("06980000000017");
    assertThat(nextValue("fixtures3", "t0699")).isEqualTo("0699-000000001-7-post");
    assertThat(nextValue("fixtures3", "t0700")).isEqualTo("0700-0000-7-00001-post");
    assertThat(nextValue("fixtures3", "t0800")).isEqualTo("0700-100000000001001-3-post");
  }

  @Test
  @Order(4)
  void autoCreateFixtures() throws Exception {
    assertThat(nextValue("OA", "default")).isEqualTo("000000001");
    assertThat(nextValue("OA", "default")).isEqualTo("000000002");
    assertThat(nextValue("OA", "notdef")).isEqualTo("000000001");
    assertThat(nextValue("Wibble", "dibble")).isEqualTo("000000001");
  }

  @Test
  @Order(5)
  void checksumUseCaseFixtures() throws Exception {
    var gen = createGenerator("fixtures5");
    createSequence(withAlgo(seq(gen, "luhnTest").put("format", "00000000").put("nextValue", 117707)
        .put("preChecksumTemplate", "22356${generated_number}")
        .put("outputTemplate", "${checksum_input_template}${checksum}"), "luhncheckdigit"));
    createSequence(withAlgo(seq(gen, "eanTest").put("format", "0000000").put("nextValue", 254)
        .put("preChecksumTemplate", "0017${generated_number}")
        .put("outputTemplate", "${checksum_input_template}${checksum}"), "ean13"));
    createSequence(withAlgo(seq(gen, "m1793").put("format", "00000000").put("nextValue", 771962)
        .put("outputTemplate", "${generated_number}${checksum}077"), "1793_ltr_mod10_r"));
    createSequence(withAlgo(seq(gen, "m12").put("format", "0000000").put("nextValue", 7298)
        .put("preChecksumTemplate", "05${generated_number}01")
        .put("outputTemplate", "${checksum_input_template}${checksum}"), "12_ltr_mod10_r"));
    createSequence(withAlgo(seq(gen, "isbn10test").put("format", "000000000").put("nextValue", 30640615)
        .put("outputTemplate",
            "${generated_number.substring(0,1)}-${generated_number.substring(1,4)}-${generated_number.substring(4,9)}-${checksum}"),
        "isbn10checkdigit"));
    createSequence(withAlgo(seq(gen, "issntest").put("format", "0000000").put("nextValue", 317847)
        .put("outputTemplate", "${generated_number.substring(0,4)}-${generated_number.substring(4,7)}${checksum}"),
        "issncheckdigit"));
    createSequence(withAlgo(seq(gen, "issntestx").put("format", "0000000").put("nextValue", 1050124)
        .put("outputTemplate", "${generated_number.substring(0,4)}-${generated_number.substring(4,7)}${checksum}"),
        "issncheckdigit"));

    assertThat(nextValue("fixtures5", "luhnTest")).isEqualTo("22356001177070");
    assertThat(nextValue("fixtures5", "eanTest")).isEqualTo("001700002547");
    assertThat(nextValue("fixtures5", "m1793")).isEqualTo("007719628077");
    assertThat(nextValue("fixtures5", "m12")).isEqualTo("050007298013");
    assertThat(nextValue("fixtures5", "isbn10test")).isEqualTo("0-306-40615-2");
    assertThat(nextValue("fixtures5", "issntest")).isEqualTo("0317-8471");
    assertThat(nextValue("fixtures5", "issntestx")).isEqualTo("1050-124X");
  }

  @Test
  @Order(6)
  void maximumGuardFixtures() throws Exception {
    var gen = createGenerator("fixtures6");
    createSequence(seq(gen, "atMax").put("format", "000").put("nextValue", 5).put("maximumNumber", 5));
    createSequence(seq(gen, "overThr").put("format", "000").put("nextValue", 5)
        .put("maximumNumber", 10).put("maximumNumberThreshold", 3));
    createSequence(seq(gen, "exceeded").put("format", "000").put("nextValue", 6).put("maximumNumber", 5));
    createSequence(seq(gen, "thrEqMax").put("format", "000").put("nextValue", 5)
        .put("maximumNumber", 5).put("maximumNumberThreshold", 5));
    createSequence(seq(gen, "farBelow").put("format", "000").put("nextValue", 2)
        .put("maximumNumber", 1000).put("maximumNumberThreshold", 900));

    var atMax = next("fixtures6", "atMax");
    assertThat(atMax.path("status").asText()).isEqualTo("WARNING");
    assertThat(atMax.path("warningCode").asText()).isEqualTo("HitMaximum");
    assertThat(atMax.path("nextValue").asText()).isEqualTo("005");

    var overThr = next("fixtures6", "overThr");
    assertThat(overThr.path("status").asText()).isEqualTo("WARNING");
    assertThat(overThr.path("warningCode").asText()).isEqualTo("OverThreshold");
    assertThat(overThr.path("nextValue").asText()).isEqualTo("005");

    var exceeded = next("fixtures6", "exceeded");
    assertThat(exceeded.path("status").asText()).isEqualTo("ERROR");
    assertThat(exceeded.path("errorCode").asText()).isEqualTo("MaxReached");
    assertThat(exceeded.has("nextValue")).isFalse();
    // rollback keeps nextValue unconsumed — the sequence stays exhausted
    var exceededAgain = next("fixtures6", "exceeded");
    assertThat(exceededAgain.path("errorCode").asText()).isEqualTo("MaxReached");

    var thrEqMax = next("fixtures6", "thrEqMax");
    assertThat(thrEqMax.path("warningCode").asText()).isEqualTo("HitMaximum");

    var farBelow = next("fixtures6", "farBelow");
    assertThat(farBelow.path("status").asText()).isEqualTo("OK");
    assertThat(farBelow.path("nextValue").asText()).isEqualTo("002");
  }

  @Test
  @Order(7)
  void yearTokenFixtures() throws Exception {
    var gen = createGenerator("fixtures7");
    createSequence(seq(gen, "ytReset").put("format", "000").put("nextValue", 150)
        .put("outputTemplate", "${current_year}-${generated_number}")
        .put("resetOnYearChange", true).put("lastUsedYear", "2020"));
    createSequence(seq(gen, "ytNoReset").put("format", "000").put("nextValue", 150)
        .put("outputTemplate", "${current_year}-${generated_number}")
        .put("lastUsedYear", "2020"));
    createSequence(seq(gen, "ytUnpadded").put("format", "###").put("nextValue", 150)
        .put("outputTemplate", "${current_year}-${generated_number}")
        .put("resetOnYearChange", true).put("lastUsedYear", "2020"));
    createSequence(seq(gen, "ytAbc").put("format", "000")
        .put("outputTemplate", "${current_year}-ABC ${generated_number}"));
    createSequence(seq(gen, "yt01a").put("format", "0000")
        .put("outputTemplate", "01A-${current_year}-${generated_number}"));

    assertThat(nextValue("fixtures7", "ytReset")).isEqualTo(YEAR + "-001");
    assertThat(nextValue("fixtures7", "ytReset")).isEqualTo(YEAR + "-002");
    assertThat(nextValue("fixtures7", "ytNoReset")).isEqualTo(YEAR + "-150");
    assertThat(nextValue("fixtures7", "ytNoReset")).isEqualTo(YEAR + "-151");
    assertThat(nextValue("fixtures7", "ytUnpadded")).isEqualTo(YEAR + "-1");
    assertThat(nextValue("fixtures7", "ytUnpadded")).isEqualTo(YEAR + "-2");
    assertThat(nextValue("fixtures7", "ytAbc")).isEqualTo(YEAR + "-ABC 001");
    assertThat(nextValue("fixtures7", "yt01a")).isEqualTo("01A-" + YEAR + "-0001");
  }

  @Test
  @Order(8)
  void yearResetFlagWithoutTokenIsRejected() throws Exception {
    var gen = createGenerator("fixtures8");
    var body = seq(gen, "invalid").put("format", "000")
        .put("outputTemplate", "${generated_number}")
        .put("resetOnYearChange", true);
    mockMvc.perform(post("/servint/numberGeneratorSequences")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content(body.toString()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @Order(9)
  void sequenceListingCarriesOwnerSnippetAndStatsEnvelope() throws Exception {
    var gen = createGenerator("fixtures9");
    createSequence(seq(gen, "listed").put("format", "000"));

    MvcResult result = mockMvc.perform(get("/servint/numberGeneratorSequences")
            .param("filters", "owner.code==fixtures9")
            .param("stats", "true")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andReturn();
    var envelope = json.readTree(result.getResponse().getContentAsString());
    assertThat(envelope.path("totalRecords").asLong()).isEqualTo(1);
    var row = envelope.path("results").get(0);
    assertThat(row.path("code").asText()).isEqualTo("listed");
    assertThat(row.path("owner").path("code").asText()).isEqualTo("fixtures9");
    assertThat(row.path("owner").path("id").asText()).isEqualTo(gen);
  }

  @Test
  @Order(10)
  void timerResetsOnlyStaleResetEnabledSequences() throws Exception {
    var gen = createGenerator("fixtures10");
    var resetId = createSequence(seq(gen, "timerReset").put("format", "000").put("nextValue", 5)
        .put("outputTemplate", "${current_year}-${generated_number}")
        .put("resetOnYearChange", true).put("lastUsedYear", "2020"));
    var controlId = createSequence(seq(gen, "timerControl").put("format", "000").put("nextValue", 5)
        .put("lastUsedYear", "2020"));

    MvcResult result = mockMvc.perform(post("/servint/numberGenerators/resetYearSequences")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andReturn();
    var summary = json.readTree(result.getResponse().getContentAsString());
    assertThat(summary.path("currentYear").asText()).isEqualTo(YEAR);
    assertThat(summary.path("sequencesReset").asInt()).isEqualTo(1);

    var reset = getSequence(resetId);
    assertThat(reset.path("nextValue").asInt()).isEqualTo(1);
    assertThat(reset.path("lastUsedYear").asText()).isEqualTo(YEAR);
    var control = getSequence(controlId);
    assertThat(control.path("nextValue").asInt()).isEqualTo(5);
    assertThat(control.path("lastUsedYear").asText()).isEqualTo("2020");
  }

  @Test
  @Order(11)
  void sequenceValuesAbove32BitsSurviveTheWire() throws Exception {
    // Legacy columns are BIGINT; values above Integer.MAX_VALUE must neither be
    // rejected on write nor wrap on read (review finding F-03).
    var gen = createGenerator("fixtures11");
    var id = createSequence(seq(gen, "bigValues")
        .put("nextValue", 2147483648L)
        .put("maximumNumber", 4294967296L)
        .put("maximumNumberThreshold", 3000000000L));

    var row = getSequence(id);
    assertThat(row.path("nextValue").asLong()).isEqualTo(2147483648L);
    assertThat(row.path("maximumNumber").asLong()).isEqualTo(4294967296L);
    assertThat(row.path("maximumNumberThreshold").asLong()).isEqualTo(3000000000L);

    // Adopted-row equivalent: a value written to the BIGINT column outside the
    // DTO write path must serialize unwrapped.
    jdbc.update("UPDATE " + TENANT + "_mod_service_interaction.number_generator_sequence"
        + " SET ngs_next_value = 8589934592 WHERE ngs_id = ?", id);
    assertThat(getSequence(id).path("nextValue").asLong()).isEqualTo(8589934592L);
  }
}
