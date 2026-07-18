package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R9 error-envelope and parameter-handling matrix (review F-14): every
 * error/parameter probe answers with a stable, documented mapping — either
 * legacy-identical (lenient kiwt params) or a registered dossier deviation
 * (D-22..D-31). Sanitation is part of the contract: no SQL, constraint or
 * parser detail on the wire.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ErrorEnvelopeMatrixIT {

  private static final String TENANT = "errmatrix";
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

  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void enableTenant() throws Exception {
    if (!tenantInitialized) {
      mockMvc.perform(post("/_/tenant")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-okapi-tenant", TENANT)
              .header("x-okapi-url", "http://localhost:9130")
              .content("{\"module_to\": \"mod-service-interaction-5.0.0\"}"))
          .andExpect(status().is2xxSuccessful());
      tenantInitialized = true;
    }
  }

  private String createGenerator(String code) throws Exception {
    var body = json.createObjectNode().put("code", code).put("name", code);
    var result = mockMvc.perform(post("/servint/numberGenerators")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content(body.toString()))
        .andExpect(status().isCreated())
        .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  // ------------------------------------------------------------ case 1: D-22

  @Test
  @Order(1)
  void malformedJsonBodyAnswers400WithEnvelope() throws Exception {
    var body = mockMvc.perform(post("/servint/numberGenerators")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content("{\"code\":\"broken\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("malformed.json"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
        .andReturn().getResponse().getContentAsString();
    // sanitation: no Jackson parse detail (body excerpts, offsets) on the wire
    assertThat(body).doesNotContain("broken").doesNotContainIgnoringCase("jackson");
  }

  // -------------------------------------- case 2: lenient kiwt params (200)

  @Test
  @Order(2)
  void invalidBooleanStatsBehavesAsAbsent() throws Exception {
    // legacy params.boolean('stats') yields null for garbage -> plain array 200
    mockMvc.perform(get("/servint/numberGenerators")
            .param("stats", "notabool")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
    // parseable values keep their strict meaning
    mockMvc.perform(get("/servint/numberGenerators")
            .param("stats", "true")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results").isArray())
        .andExpect(jsonPath("$.totalRecords").isNumber());
  }

  @Test
  @Order(3)
  void nonNumericPagingParamsFallBackToDefaults() throws Exception {
    // legacy params.int('perPage') yields null for garbage -> default paging 200
    mockMvc.perform(get("/servint/numberGenerators")
            .param("perPage", "abc")
            .param("page", "notanumber")
            .param("offset", "xyz")
            .param("max", "-")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  // ------------------------------------------------------------ case 3: D-28

  @Test
  @Order(4)
  void invalidBooleanFilterCoercesToFalse() throws Exception {
    // legacy answers 500 (ConversionFailedException;
    // docs/migration/evidence/r13-legacy/observations.md, f21-invalid-boolean);
    // the port's coerce-to-false is registered deviation D-28 — this is NOT
    // legacy parity
    mockMvc.perform(get("/servint/numberGeneratorSequences")
            .param("filters", "enabled==notabool")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  // ------------------------------------------------------------ case 4: D-23

  @Test
  @Order(5)
  void missingTenantHeaderAnswers400WithFolioSpringMessage() throws Exception {
    mockMvc.perform(get("/servint/numberGenerators"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("x-okapi-tenant header must be provided")));
  }

  // ------------------------------------------------------------ case 5: D-24

  @Test
  @Order(6)
  void duplicateGeneratorCodeAnswersSanitized409() throws Exception {
    createGenerator("dup-code");
    var body = mockMvc.perform(post("/servint/numberGenerators")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content("{\"code\":\"dup-code\",\"name\":\"second\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("integrity.violation"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
        .andReturn().getResponse().getContentAsString();
    // sanitation: no SQL/constraint fragments on the wire
    assertThat(body)
        .doesNotContainIgnoringCase("duplicate key")
        .doesNotContainIgnoringCase("constraint")
        .doesNotContainIgnoringCase("sql")
        .doesNotContain("NumberGeneratorUniqueCode")
        .doesNotContain("ng_code");
  }

  // ------------------------------------------------------------ case 6: D-25

  @Test
  @Order(7)
  void unknownRefdataValueAnswers400WithEnvelope() throws Exception {
    var generatorId = createGenerator("refgen");
    var body = json.createObjectNode();
    body.putObject("owner").put("id", generatorId);
    body.put("code", "refseq").put("name", "refseq");
    body.putObject("checkDigitAlgo").put("value", "noSuchAlgo");
    mockMvc.perform(post("/servint/numberGeneratorSequences")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .content(body.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("unknown.refdata"))
        .andExpect(jsonPath("$.errors[0].message").value(containsString("noSuchAlgo")));
    // the rejected write left no sequence behind
    mockMvc.perform(get("/servint/numberGeneratorSequences")
            .param("filters", "code==refseq")
            .header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }
}
