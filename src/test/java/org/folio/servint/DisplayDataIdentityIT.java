package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
 * Regression for review finding F-09 (R8c, deviation D-20): the display-data
 * update trusts ONLY the path dashboard id. A body dashId naming a different
 * dashboard is rejected with 422 and the errors envelope, and no state
 * changes — the inherited legacy flaw re-pointed the authorized row at the
 * body's dashboard (cross-dashboard write). An absent or matching body
 * dashId keeps working.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DisplayDataIdentityIT {

  private static final String TENANT = "dddidentity";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";
  private static final String USER_1 = UUID.randomUUID().toString();

  private static boolean initialized;
  private static String dashA;
  private static String dashB;

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

  // ---------------------------------------------------------------- helpers

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder) {
    return builder.header("x-okapi-tenant", TENANT).header("x-okapi-user-id", USER_1);
  }

  private String createDashboard(String name) throws Exception {
    var result = mockMvc.perform(as(post("/servint/dashboard"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("name", name).toString()))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).path("id").asText();
  }

  private MockHttpServletRequestBuilder putDisplayData(String dashboardId, String body) {
    return as(put("/servint/dashboard/" + dashboardId + "/displayData"))
        .contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private String dashIdOfDisplayDataRow(String dashId) {
    return jdbcTemplate.queryForObject(
        "select ddd_dash_id from " + SCHEMA + ".dashboard_display_data where ddd_dash_id = ?",
        String.class, dashId);
  }

  // ------------------------------------------------------------------ tests

  @BeforeEach
  void setUp() throws Exception {
    if (initialized) {
      return;
    }
    mockMvc.perform(post("/_/tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-okapi-tenant", TENANT)
            .header("x-okapi-url", "http://localhost:9130")
            .content("{\"module_to\": \"mod-service-interaction-5.0.0\"}"))
        .andExpect(status().is2xxSuccessful());
    dashA = createDashboard("Dashboard A");
    dashB = createDashboard("Dashboard B");
    initialized = true;
  }

  @Test
  @Order(1)
  void mismatchedBodyDashIdIsRejectedWithoutStateChange() throws Exception {
    // Review reproduction (cross-dashboard write): PUT on dashboard A with
    // dashboard B's id in the body. Legacy re-pointed A's row at B.
    mockMvc.perform(putDisplayData(dashA,
            json.createObjectNode().put("dashId", dashB)
                .put("layoutData", "{\"hijacked\":true}").toString()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errors[0].code").value("dashboard.id.mismatch"));

    // No state change: both rows keep their identity, nothing was orphaned
    // or reassigned, and the rejected layoutData was not written.
    assertThat(dashIdOfDisplayDataRow(dashA)).isEqualTo(dashA);
    assertThat(dashIdOfDisplayDataRow(dashB)).isEqualTo(dashB);
    assertThat(jdbcTemplate.queryForObject(
        "select count(*) from " + SCHEMA + ".dashboard_display_data where ddd_layout_data like '%hijacked%'",
        Long.class)).isZero();
    mockMvc.perform(as(get("/servint/dashboard/" + dashA + "/displayData")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dashId").value(dashA));
  }

  @Test
  @Order(2)
  void matchingBodyDashIdStillUpdates() throws Exception {
    mockMvc.perform(putDisplayData(dashA,
            json.createObjectNode().put("dashId", dashA)
                .put("layoutData", "{\"cols\":12}").toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dashId").value(dashA))
        .andExpect(jsonPath("$.layoutData").value("{\"cols\":12}"));
  }

  @Test
  @Order(3)
  void absentBodyDashIdStillUpdatesWithPathIdentity() throws Exception {
    mockMvc.perform(putDisplayData(dashA,
            json.createObjectNode().put("layoutData", "{\"cols\":6}").toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dashId").value(dashA))
        .andExpect(jsonPath("$.layoutData").value("{\"cols\":6}"));
  }
}
