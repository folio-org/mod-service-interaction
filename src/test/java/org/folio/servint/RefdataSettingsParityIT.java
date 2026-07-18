package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Behavior-parity suite for the refdata + settings surface: category CRUD
 * with expanded values and all-delete-orphan collection binding, the
 * domain/property value lookup (case-insensitive domain, capitalized
 * property, 404 only for unknown domains), and web-toolkit AppSetting CRUD
 * with plain-string settingType.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RefdataSettingsParityIT {

  private static final String TENANT = "rdparity";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";
  private static boolean initialized;
  private static String categoryId;
  private static String firstValueId;
  private static String settingId;

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

  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void enableTenant() throws Exception {
    if (!initialized) {
      mockMvc.perform(post("/_/tenant")
              .contentType(MediaType.APPLICATION_JSON)
              .header("x-okapi-tenant", TENANT)
              .header("x-okapi-url", "http://localhost:9130")
              .content("{\"module_to\": \"mod-service-interaction-5.0.0\","
                  + " \"parameters\": [{\"key\": \"loadReference\", \"value\": \"true\"}]}"))
          .andExpect(status().is2xxSuccessful());
      initialized = true;
    }
  }

  // ---------------------------------------------------------------- helpers

  private MockHttpServletRequestBuilder tenanted(MockHttpServletRequestBuilder request) {
    return request.header("x-okapi-tenant", TENANT);
  }

  private JsonNode getJson(String url, int expectedStatus, String... queryPairs) throws Exception {
    var request = tenanted(get(url));
    for (var i = 0; i < queryPairs.length; i += 2) {
      request = request.queryParam(queryPairs[i], queryPairs[i + 1]);
    }
    var result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
    var body = result.getResponse().getContentAsString();
    return body.isEmpty() ? null : json.readTree(body);
  }

  private JsonNode send(MockHttpServletRequestBuilder request, String body, int expectedStatus)
      throws Exception {
    var result = mockMvc.perform(tenanted(request).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().is(expectedStatus)).andReturn();
    var content = result.getResponse().getContentAsString();
    return content.isEmpty() ? null : json.readTree(content);
  }

  private List<String> extract(JsonNode array, String field) {
    var extracted = new ArrayList<String>();
    array.forEach(node -> extracted.add(node.path(field).asText()));
    return extracted;
  }

  // ------------------------------------------------------------------ tests

  @Test
  @Order(1)
  void seededCategoriesListWithValuesExpanded() throws Exception {
    var plain = getJson("/servint/refdata", 200);
    assertThat(extract(plain, "desc")).containsExactlyInAnyOrder(
        "NumberGeneratorSequence.CheckDigitAlgo", "NumberGeneratorSequence.MaximumCheck",
        "DashboardAccess.Access");
    for (var category : plain) {
      assertThat(category.path("internal").asBoolean()).isTrue();
      assertThat(category.path("values").isArray()).isTrue();
      // Category-embedded values do NOT carry the owner back-reference.
      for (var value : category.path("values")) {
        assertThat(value.has("owner")).isFalse();
      }
    }

    var stats = getJson("/servint/refdata", 200, "stats", "true");
    assertThat(stats.path("totalRecords").asLong()).isEqualTo(3);
    assertThat(stats.path("results").isArray()).isTrue();

    var filtered = getJson("/servint/refdata", 200, "filters", "desc==DashboardAccess.Access");
    assertThat(filtered).hasSize(1);
    assertThat(extract(filtered.get(0).path("values"), "value"))
        .containsExactlyInAnyOrder("manage", "edit", "view");
  }

  @Test
  @Order(2)
  void lookupResolvesDomainAndPropertyLikeTheLegacyRegistry() throws Exception {
    var checkDigits = getJson("/servint/refdata/NumberGeneratorSequence/checkDigitAlgo", 200);
    assertThat(extract(checkDigits, "value")).containsExactlyInAnyOrder(
        "none", "ean13", "1793_ltr_mod10_r", "12_ltr_mod10_r",
        "isbn10checkdigit", "issncheckdigit", "luhncheckdigit");
    for (var value : checkDigits) {
      assertThat(value.hasNonNull("id")).isTrue();
      assertThat(value.hasNonNull("label")).isTrue();
      // Standalone value renders embed the owning category (M4-verified).
      assertThat(value.path("owner").path("desc").asText())
          .isEqualTo("NumberGeneratorSequence.CheckDigitAlgo");
      assertThat(value.path("owner").hasNonNull("id")).isTrue();
      assertThat(value.path("owner").path("internal").asBoolean()).isTrue();
    }

    // Domain matching is case-insensitive; the property is capitalized.
    assertThat(extract(getJson("/servint/refdata/dashboardaccess/access", 200), "value"))
        .containsExactlyInAnyOrder("manage", "edit", "view");

    // term + match filter the value listing.
    assertThat(extract(getJson("/servint/refdata/NumberGeneratorSequence/checkDigitAlgo", 200,
        "match", "value", "term", "mod10"), "value"))
        .containsExactlyInAnyOrder("1793_ltr_mod10_r", "12_ltr_mod10_r");

    // Unknown domain: 404. Known domain, unregistered property: empty 200.
    getJson("/servint/refdata/NoSuchDomain/anything", 404);
    assertThat(getJson("/servint/refdata/Dashboard/bogus", 200)).isEmpty();
  }

  @Test
  @Order(3)
  void categoryCrudWithAllDeleteOrphanValueBinding() throws Exception {
    var created = send(post("/servint/refdata"),
        "{\"desc\": \"Test.Category\", \"values\": ["
            + "{\"label\": \"First Thing\"}, {\"label\": \"Second\", \"value\": \"custom_val\"}]}",
        201);
    categoryId = created.path("id").asText();
    assertThat(created.path("internal").asBoolean()).isFalse();
    assertThat(extract(created.path("values"), "value"))
        .containsExactlyInAnyOrder("first_thing", "custom_val");
    firstValueId = created.path("values").findValuesAsText("id").stream()
        .filter(id -> !id.isEmpty()).findFirst().orElseThrow();
    for (var value : created.path("values")) {
      if (value.path("value").asText().equals("first_thing")) {
        firstValueId = value.path("id").asText();
      }
    }

    // PUT: renamed first value survives, unmentioned value is orphan-removed,
    // a new value is created with the normalized label.
    var updated = send(put("/servint/refdata/" + categoryId),
        "{\"values\": [{\"id\": \"" + firstValueId + "\", \"label\": \"Renamed\"},"
            + " {\"label\": \"Third Entry\"}]}",
        200);
    assertThat(updated.path("desc").asText()).isEqualTo("Test.Category");
    assertThat(extract(updated.path("values"), "value"))
        .containsExactlyInAnyOrder("first_thing", "third_entry");
    assertThat(extract(updated.path("values"), "label"))
        .containsExactlyInAnyOrder("Renamed", "Third Entry");
    assertThat(jdbcTemplate.queryForObject("select count(*) from " + SCHEMA
        + ".refdata_value where rdv_owner = ?", Long.class, categoryId)).isEqualTo(2);

    // DELETE cascades the remaining values; the category is gone.
    mockMvc.perform(tenanted(delete("/servint/refdata/" + categoryId)))
        .andExpect(status().isNoContent());
    assertThat(jdbcTemplate.queryForObject("select count(*) from " + SCHEMA
        + ".refdata_value where rdv_owner = ?", Long.class, categoryId)).isZero();
    getJson("/servint/refdata/" + categoryId, 404);
  }

  @Test
  @Order(4)
  void appSettingCrudRendersPlainStringSettingType() throws Exception {
    var created = send(post("/servint/settings/appSettings"),
        "{\"section\": \"numberGenerators\", \"key\": \"displayWarnings\","
            + " \"settingType\": \"String\", \"value\": \"true\", \"hidden\": false}",
        201);
    settingId = created.path("id").asText();
    assertThat(created.path("settingType").isTextual()).isTrue();
    assertThat(created.path("settingType").asText()).isEqualTo("String");
    assertThat(created.path("hidden").asBoolean()).isFalse();
    assertThat(created.path("value").asText()).isEqualTo("true");

    var stats = getJson("/servint/settings/appSettings", 200, "stats", "true");
    assertThat(stats.path("totalRecords").asLong()).isEqualTo(1);

    var filtered = getJson("/servint/settings/appSettings", 200,
        "filters", "section==numberGenerators");
    assertThat(filtered).hasSize(1);

    // Partial PUT changes only the value; section and key survive.
    var updated = send(put("/servint/settings/appSettings/" + settingId),
        "{\"value\": \"false\"}", 200);
    assertThat(updated.path("value").asText()).isEqualTo("false");
    assertThat(updated.path("section").asText()).isEqualTo("numberGenerators");
    assertThat(updated.path("key").asText()).isEqualTo("displayWarnings");

    // Legacy GORM validation answers 422 with an errors envelope on create.
    var invalid = send(post("/servint/settings/appSettings"), "{\"section\": \"noKey\"}", 422);
    assertThat(invalid.path("errors").isArray()).isTrue();

    getJson("/servint/settings/appSettings/" + settingId, 200);
    mockMvc.perform(tenanted(delete("/servint/settings/appSettings/" + settingId)))
        .andExpect(status().isNoContent());
    getJson("/servint/settings/appSettings/" + settingId, 404);
    assertThat(getJson("/servint/settings/appSettings", 200)).isEmpty();
  }
}
