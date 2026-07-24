package org.folio.servint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Behavior-parity suite for the dashboards/widgets slice, pinning the legacy
 * contracts from dossier §5.1–§5.3 and §5.9: access hierarchy with admin
 * override, my-dashboards auto-provisioning, editUserDashboards ordering
 * rules, editDashboardUsers grant rules, widget-instance authorization and
 * weight auto-assignment (flat definitionName/definitionVersion request
 * binding), display-data access, and the admin maintenance actions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardParityIT {

  private static final String TENANT = "dashparity";
  private static final String SCHEMA = TENANT + "_mod_service_interaction";
  private static final String USER_1 = UUID.randomUUID().toString();
  private static final String USER_2 = UUID.randomUUID().toString();
  private static final String USER_3 = UUID.randomUUID().toString();
  private static final String ADMIN_PERMS = "[\"servint.dashboards.admin.allops\"]";
  private static boolean tenantInitialized;

  private static String dash1;
  private static String dash2;
  private static String accessId1;
  private static String accessId2;
  private static String user2AccessId;
  private static String instance1;

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

  @Autowired
  private org.folio.servint.service.dashboard.DashboardService dashboardService;

  @Autowired
  private org.folio.spring.FolioModuleMetadata moduleMetadata;

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

  // ---------------------------------------------------------------- helpers

  private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String userId) {
    return builder.header("x-okapi-tenant", TENANT).header("x-okapi-user-id", userId);
  }

  private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder, String userId) {
    return as(builder, userId).header("x-okapi-permissions", ADMIN_PERMS);
  }

  private JsonNode read(org.springframework.test.web.servlet.MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode myDashboards(String userId) throws Exception {
    return read(mockMvc.perform(as(get("/servint/dashboard/my-dashboards"), userId))
        .andExpect(status().isOk()).andReturn());
  }

  private JsonNode dashboardUsers(String dashboardId, String userId) throws Exception {
    return read(mockMvc.perform(as(get("/servint/dashboard/" + dashboardId + "/users"), userId))
        .andExpect(status().isOk()).andReturn());
  }

  private JsonNode postUsers(String dashboardId, String userId, ArrayNode items) throws Exception {
    return read(mockMvc.perform(as(post("/servint/dashboard/" + dashboardId + "/users"), userId)
            .contentType(MediaType.APPLICATION_JSON).content(items.toString()))
        .andExpect(status().isOk()).andReturn());
  }

  private String createDashboard(String userId, String name) throws Exception {
    var result = mockMvc.perform(as(post("/servint/dashboard"), userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("name", name).toString()))
        .andExpect(status().isCreated()).andReturn();
    return read(result).path("id").asText();
  }

  // ------------------------------------------------------------------ tests

  @Test
  @Order(1)
  void myDashboardsAutoProvisionsDefaultDashboard() throws Exception {
    var list = myDashboards(USER_1);
    assertThat(list).hasSize(1);
    var access = list.get(0);
    assertThat(access.path("dashboard").path("name").asText()).isEqualTo("My dashboard");
    assertThat(access.path("access").path("value").asText()).isEqualTo("manage");
    assertThat(access.path("userDashboardWeight").asInt()).isZero();
    assertThat(access.path("defaultUserDashboard").asBoolean()).isTrue();
    assertThat(access.hasNonNull("dateCreated")).isTrue();
    dash1 = access.path("dashboard").path("id").asText();
    accessId1 = access.path("id").asText();

    // Idempotent: a second call never provisions a duplicate.
    assertThat(myDashboards(USER_1)).hasSize(1);

    // The provisioned dashboard got an empty display-data record.
    var ddd = read(mockMvc.perform(as(get("/servint/dashboard/" + dash1 + "/displayData"), USER_1))
        .andExpect(status().isOk()).andReturn());
    assertThat(ddd.path("dashId").asText()).isEqualTo(dash1);
  }

  @Test
  @Order(2)
  void createDashboardProvisionsManageAccess() throws Exception {
    var result = mockMvc.perform(as(post("/servint/dashboard"), USER_1)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("name", "Second board").toString()))
        .andExpect(status().isCreated()).andReturn();
    var dto = read(result);
    dash2 = dto.path("id").asText();
    assertThat(dto.path("widgets").isArray()).isTrue();
    assertThat(dto.path("widgets")).isEmpty();

    var list = myDashboards(USER_1);
    assertThat(list).hasSize(2);
    for (var access : list) {
      if (access.path("dashboard").path("id").asText().equals(dash2)) {
        assertThat(access.path("access").path("value").asText()).isEqualTo("manage");
        assertThat(access.path("userDashboardWeight").asInt()).isEqualTo(1);
        assertThat(access.path("defaultUserDashboard").asBoolean()).isFalse();
        accessId2 = access.path("id").asText();
      }
    }
    assertThat(accessId2).isNotNull();
  }

  @Test
  @Order(3)
  void accessHierarchyAndAdminOverride() throws Exception {
    // No access object -> 403 (before any 404, exactly like legacy).
    mockMvc.perform(as(get("/servint/dashboard/" + dash1), USER_2)).andExpect(status().isForbidden());
    mockMvc.perform(as(get("/servint/dashboard/" + dash1 + "/my-access"), USER_2))
        .andExpect(status().isForbidden());

    // Index is admin-only.
    mockMvc.perform(as(get("/servint/dashboard"), USER_2)).andExpect(status().isForbidden());
    var all = read(mockMvc.perform(asAdmin(get("/servint/dashboard"), USER_3))
        .andExpect(status().isOk()).andReturn());
    assertThat(all.size()).isGreaterThanOrEqualTo(2);

    // Admin authority bypasses per-dashboard access.
    mockMvc.perform(asAdmin(get("/servint/dashboard/" + dash1), USER_3)).andExpect(status().isOk());

    var myAccess = read(mockMvc.perform(as(get("/servint/dashboard/" + dash1 + "/my-access"), USER_1))
        .andExpect(status().isOk()).andReturn());
    assertThat(myAccess.path("access").asText()).isEqualTo("manage");
  }

  @Test
  @Order(4)
  void editDashboardUsersGrantRules() throws Exception {
    // Grant view to user 2; the users render carries the dashboard as an
    // id-only reference (M4-verified legacy shape).
    var items = json.createArrayNode();
    var grant = items.addObject();
    grant.putObject("user").put("id", USER_2);
    grant.put("access", "view");
    var users = postUsers(dash1, USER_1, items);
    assertThat(users).hasSize(2);
    for (var access : users) {
      assertThat(access.path("dashboard").path("id").asText()).isEqualTo(dash1);
      assertThat(access.path("dashboard").has("name")).isFalse();
      assertThat(access.path("dashboard").has("widgets")).isFalse();
      assertThat(access.path("dateCreated").asText())
          .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
      if (access.path("user").path("id").asText().equals(USER_2)) {
        user2AccessId = access.path("id").asText();
        assertThat(access.path("access").path("value").asText()).isEqualTo("view");
        // First dashboard for user 2: weight 0 and elected default.
        assertThat(access.path("userDashboardWeight").asInt()).isZero();
        assertThat(access.path("defaultUserDashboard").asBoolean()).isTrue();
      }
    }
    assertThat(user2AccessId).isNotNull();
    mockMvc.perform(as(get("/servint/dashboard/" + dash1), USER_2)).andExpect(status().isOk());
    // view < edit: user 2 cannot update the dashboard.
    mockMvc.perform(as(put("/servint/dashboard/" + dash1), USER_2)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("name", "nope").toString()))
        .andExpect(status().isForbidden());

    // Re-granting an already-covered user is ignored.
    assertThat(postUsers(dash1, USER_1, items)).hasSize(2);

    // Items for the caller's own access are ignored.
    var own = json.createArrayNode();
    var ownItem = own.addObject();
    ownItem.putObject("user").put("id", USER_1);
    ownItem.put("access", "view");
    var afterOwn = postUsers(dash1, USER_1, own);
    for (var access : afterOwn) {
      if (access.path("user").path("id").asText().equals(USER_1)) {
        assertThat(access.path("access").path("value").asText()).isEqualTo("manage");
      }
    }

    // Editing an existing item may change ONLY the access level.
    var edit = json.createArrayNode();
    var editItem = edit.addObject();
    editItem.put("id", user2AccessId);
    editItem.putObject("user").put("id", USER_2);
    editItem.put("access", "edit");
    editItem.put("userDashboardWeight", 99);
    var afterEdit = postUsers(dash1, USER_1, edit);
    for (var access : afterEdit) {
      if (access.path("id").asText().equals(user2AccessId)) {
        assertThat(access.path("access").path("value").asText()).isEqualTo("edit");
        assertThat(access.path("userDashboardWeight").asInt()).isZero();
      }
    }

    // A dashboard-id mismatch is ignored: posting dash1's access object to dash2.
    var mismatch = json.createArrayNode();
    var mismatchItem = mismatch.addObject();
    mismatchItem.put("id", user2AccessId);
    mismatchItem.putObject("user").put("id", USER_2);
    mismatchItem.put("access", "view");
    postUsers(dash2, USER_1, mismatch);
    for (var access : dashboardUsers(dash1, USER_1)) {
      if (access.path("id").asText().equals(user2AccessId)) {
        assertThat(access.path("access").path("value").asText()).isEqualTo("edit");
      }
    }

    // _delete removes the grant; user 2 loses access.
    var deletion = json.createArrayNode();
    var deleteItem = deletion.addObject();
    deleteItem.put("id", user2AccessId);
    deleteItem.putObject("user").put("id", USER_2);
    deleteItem.put("_delete", true);
    assertThat(postUsers(dash1, USER_1, deletion)).hasSize(1);
    mockMvc.perform(as(get("/servint/dashboard/" + dash1), USER_2)).andExpect(status().isForbidden());

    // Restore view for user 2 (used by later tests).
    var regrant = postUsers(dash1, USER_1, items);
    for (var access : regrant) {
      if (access.path("user").path("id").asText().equals(USER_2)) {
        user2AccessId = access.path("id").asText();
      }
    }
  }

  @Test
  @Order(5)
  void editUserDashboardsOrderingRules() throws Exception {
    // Whole-request rejection: missing id -> 400.
    var noId = json.createArrayNode();
    noId.addObject().putObject("user").put("id", USER_1);
    mockMvc.perform(as(put("/servint/dashboard/my-dashboards"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(noId.toString()))
        .andExpect(status().isBadRequest());

    // Missing user id -> 400.
    var noUser = json.createArrayNode();
    noUser.addObject().put("id", accessId1);
    mockMvc.perform(as(put("/servint/dashboard/my-dashboards"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(noUser.toString()))
        .andExpect(status().isBadRequest());

    // Foreign user -> 403.
    var foreign = json.createArrayNode();
    var foreignItem = foreign.addObject();
    foreignItem.put("id", accessId1);
    foreignItem.putObject("user").put("id", USER_2);
    mockMvc.perform(as(put("/servint/dashboard/my-dashboards"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(foreign.toString()))
        .andExpect(status().isForbidden());

    // Valid bulk update: reorder and elect a new default (clears the old one).
    var reorder = json.createArrayNode();
    var first = reorder.addObject();
    first.put("id", accessId1);
    first.putObject("user").put("id", USER_1);
    first.put("userDashboardWeight", 5);
    var second = reorder.addObject();
    second.put("id", accessId2);
    second.putObject("user").put("id", USER_1);
    second.put("userDashboardWeight", 1);
    second.put("defaultUserDashboard", true);
    var updated = read(mockMvc.perform(as(put("/servint/dashboard/my-dashboards"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(reorder.toString()))
        .andExpect(status().isOk()).andReturn());
    for (var access : updated) {
      if (access.path("id").asText().equals(accessId1)) {
        assertThat(access.path("userDashboardWeight").asInt()).isEqualTo(5);
        assertThat(access.path("defaultUserDashboard").asBoolean()).isFalse();
      }
      if (access.path("id").asText().equals(accessId2)) {
        assertThat(access.path("userDashboardWeight").asInt()).isEqualTo(1);
        assertThat(access.path("defaultUserDashboard").asBoolean()).isTrue();
      }
    }

    // defaultUserDashboard never transitions true -> false by request.
    var demote = json.createArrayNode();
    var demoteItem = demote.addObject();
    demoteItem.put("id", accessId2);
    demoteItem.putObject("user").put("id", USER_1);
    demoteItem.put("userDashboardWeight", 1);
    demoteItem.put("defaultUserDashboard", false);
    var afterDemote = read(mockMvc.perform(as(put("/servint/dashboard/my-dashboards"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(demote.toString()))
        .andExpect(status().isOk()).andReturn());
    for (var access : afterDemote) {
      if (access.path("id").asText().equals(accessId2)) {
        assertThat(access.path("defaultUserDashboard").asBoolean()).isTrue();
      }
    }
  }

  @Test
  @Order(6)
  void widgetInstanceLifecycleAndAuthorization() throws Exception {
    // Requests carry the FLAT definitionName/definitionVersion pair.
    var body = json.createObjectNode()
        .put("name", "wi1")
        .put("definitionName", "ERM Agreements")
        .put("definitionVersion", "1.0")
        .put("configuration", "{\"key\":\"v\"}");
    body.putObject("owner").put("id", dash1);
    var created = read(mockMvc.perform(as(post("/servint/widgets/instances"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isCreated()).andReturn());
    instance1 = created.path("id").asText();
    assertThat(created.path("weight").asInt()).isZero();
    assertThat(created.path("definition").path("name").asText()).isEqualTo("ERM Agreements");
    assertThat(created.path("definition").path("version").asText()).isEqualTo("1.0");
    // The flat pair is a request-side binding only — never rendered.
    assertThat(created.has("definitionName")).isFalse();
    assertThat(created.has("definitionVersion")).isFalse();
    assertThat(created.path("owner").path("id").asText()).isEqualTo(dash1);

    // Auto-increment: next instance without weight gets max + 1.
    var second = read(mockMvc.perform(as(post("/servint/widgets/instances"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(body.deepCopy().put("name", "wi2").toString()))
        .andExpect(status().isCreated()).andReturn());
    assertThat(second.path("weight").asInt()).isEqualTo(1);

    // An explicit weight is preserved.
    var third = read(mockMvc.perform(as(post("/servint/widgets/instances"), USER_1)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.deepCopy().put("name", "wi3").put("weight", 7).toString()))
        .andExpect(status().isCreated()).andReturn());
    assertThat(third.path("weight").asInt()).isEqualTo(7);

    // Unknown owner -> 404.
    var orphan = (ObjectNode) body.deepCopy();
    orphan.putObject("owner").put("id", UUID.randomUUID().toString());
    mockMvc.perform(as(post("/servint/widgets/instances"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(orphan.toString()))
        .andExpect(status().isNotFound());

    // view access cannot create; it can read.
    mockMvc.perform(as(post("/servint/widgets/instances"), USER_2)
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isForbidden());
    mockMvc.perform(as(get("/servint/widgets/instances/" + instance1), USER_2))
        .andExpect(status().isOk());
    mockMvc.perform(as(put("/servint/widgets/instances/" + instance1), USER_2)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("name", "nope").toString()))
        .andExpect(status().isForbidden());

    // Owner (manage ⊃ edit) can update.
    var renamed = read(mockMvc.perform(as(put("/servint/widgets/instances/" + instance1), USER_1)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("name", "wi1-renamed").toString()))
        .andExpect(status().isOk()).andReturn());
    assertThat(renamed.path("name").asText()).isEqualTo("wi1-renamed");
    assertThat(renamed.path("weight").asInt()).isZero();

    // Dashboard widgets listing (view suffices).
    var widgets = read(mockMvc.perform(as(get("/servint/dashboard/" + dash1 + "/widgets"), USER_2))
        .andExpect(status().isOk()).andReturn());
    assertThat(widgets).hasSize(3);

    // The dashboard render carries ONLY widget summaries {id, weight, name}.
    var dashboard = read(mockMvc.perform(as(get("/servint/dashboard/" + dash1), USER_1))
        .andExpect(status().isOk()).andReturn());
    assertThat(dashboard.path("widgets")).hasSize(3);
    for (var summary : dashboard.path("widgets")) {
      assertThat(summary.has("id")).isTrue();
      assertThat(summary.has("weight")).isTrue();
      assertThat(summary.has("name")).isTrue();
      assertThat(summary.has("configuration")).isFalse();
      assertThat(summary.has("definition")).isFalse();
    }

    // The instance index is admin-only.
    mockMvc.perform(as(get("/servint/widgets/instances"), USER_1)).andExpect(status().isForbidden());
    var all = read(mockMvc.perform(asAdmin(get("/servint/widgets/instances"), USER_3))
        .andExpect(status().isOk()).andReturn());
    assertThat(all).hasSize(3);
  }

  @Test
  @Order(7)
  void displayDataRequiresEditToUpdate() throws Exception {
    var updated = read(mockMvc.perform(as(put("/servint/dashboard/" + dash1 + "/displayData"), USER_1)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("layoutData", "{\"cols\":12}").toString()))
        .andExpect(status().isOk()).andReturn());
    assertThat(updated.path("layoutData").asText()).isEqualTo("{\"cols\":12}");
    assertThat(updated.path("dashId").asText()).isEqualTo(dash1);

    // view access cannot update display data.
    mockMvc.perform(as(put("/servint/dashboard/" + dash1 + "/displayData"), USER_2)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.createObjectNode().put("layoutData", "{}").toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @Order(8)
  void deleteDashboardCleansUpAccessAndDisplayData() throws Exception {
    var dash3 = createDashboard(USER_1, "Doomed");
    mockMvc.perform(as(delete("/servint/dashboard/" + dash3), USER_2))
        .andExpect(status().isForbidden());
    mockMvc.perform(as(delete("/servint/dashboard/" + dash3), USER_1))
        .andExpect(status().isNoContent());
    // Access rows are gone: the owner now 403s (access-first, like legacy);
    // an admin bypasses access and sees the true 404.
    mockMvc.perform(as(get("/servint/dashboard/" + dash3), USER_1)).andExpect(status().isForbidden());
    mockMvc.perform(asAdmin(get("/servint/dashboard/" + dash3), USER_3)).andExpect(status().isNotFound());
    assertThat(jdbcTemplate.queryForObject(
        "select count(*) from " + SCHEMA + ".dashboard_display_data where ddd_dash_id = ?",
        Long.class, dash3)).isZero();
    assertThat(myDashboards(USER_1)).hasSize(2);
  }

  @Test
  @Order(9)
  void adminActionsImportTypesAndEnsureDisplayData() throws Exception {
    var imported = read(mockMvc.perform(as(get("/servint/admin/triggerTypeImport"), USER_1))
        .andExpect(status().isOk()).andReturn());
    assertThat(imported.path("status").asText()).isEqualTo("OK");
    var types = read(mockMvc.perform(as(get("/servint/widgets/types"), USER_1))
        .andExpect(status().isOk()).andReturn());
    assertThat(types).hasSize(1);
    assertThat(types.get(0).path("name").asText()).isEqualTo("SimpleSearch");
    assertThat(types.get(0).path("typeVersion").asText()).isEqualTo("1.0");
    assertThat(types.get(0).path("schema").asText()).contains("SimpleSearch");

    // Import is idempotent by (name, typeVersion); the clean variant reloads.
    mockMvc.perform(as(get("/servint/admin/triggerTypeImport"), USER_1)).andExpect(status().isOk());
    mockMvc.perform(as(get("/servint/admin/triggerTypeImportClean"), USER_1)).andExpect(status().isOk());
    assertThat(read(mockMvc.perform(as(get("/servint/widgets/types"), USER_1))
        .andExpect(status().isOk()).andReturn())).hasSize(1);

    // ensureDisplayData recreates a missing record.
    jdbcTemplate.update("delete from " + SCHEMA + ".dashboard_display_data where ddd_dash_id = ?", dash1);
    mockMvc.perform(as(get("/servint/dashboard/" + dash1 + "/displayData"), USER_1))
        .andExpect(status().isNotFound());
    mockMvc.perform(as(get("/servint/admin/ensureDisplayData"), USER_1)).andExpect(status().isOk());
    var restored = read(mockMvc.perform(as(get("/servint/dashboard/" + dash1 + "/displayData"), USER_1))
        .andExpect(status().isOk()).andReturn());
    assertThat(restored.path("dashId").asText()).isEqualTo(dash1);

    // Unknown actions dispatch to nothing.
    mockMvc.perform(as(get("/servint/admin/noSuchAction"), USER_1)).andExpect(status().isNotFound());
  }

  @Test
  @Order(10)
  void localDefinitionEndpointsRenderTheDefinitionShape() throws Exception {
    var defId = UUID.randomUUID().toString();
    jdbcTemplate.update("insert into " + SCHEMA + ".widget_definition "
            + "(wdef_id, wdef_version, wdef_name, wdef_definition_version, wdef_definition, "
            + "wdef_type_name, wdef_type_version) values (?, 0, ?, ?, ?, ?, ?)",
        defId, "ERM Agreements", "1.2", "{\"baseUrl\":\"/erm/sas\"}", "SimpleSearch", "1.0");

    var list = read(mockMvc.perform(as(get("/servint/widgets/definitions"), USER_1))
        .andExpect(status().isOk()).andReturn());
    assertThat(list).hasSize(1);
    var rendered = list.get(0);
    assertThat(rendered.path("name").asText()).isEqualTo("ERM Agreements");
    assertThat(rendered.path("version").asText()).isEqualTo("1.2");
    assertThat(rendered.path("type").path("name").asText()).isEqualTo("SimpleSearch");
    assertThat(rendered.path("type").path("version").asText()).isEqualTo("1.0");
    // definition renders as the PARSED object, not the stored string.
    assertThat(rendered.path("definition").path("baseUrl").asText()).isEqualTo("/erm/sas");

    var byId = read(mockMvc.perform(as(get("/servint/widgets/definitions/" + defId), USER_1))
        .andExpect(status().isOk()).andReturn());
    // The entity id is never rendered (legacy gson includes:[], M4-verified).
    assertThat(byId.has("id")).isFalse();
    assertThat(byId.path("name").asText()).isEqualTo("ERM Agreements");
    mockMvc.perform(as(get("/servint/widgets/definitions/" + UUID.randomUUID()), USER_1))
        .andExpect(status().isNotFound());

    // The dashboard-interface endpoint serves the same render with no user.
    var served = read(mockMvc.perform(get("/dashboard/definitions").header("x-okapi-tenant", TENANT))
        .andExpect(status().isOk()).andReturn());
    assertThat(served).hasSize(1);
    assertThat(served.get(0).path("definition").path("baseUrl").asText()).isEqualTo("/erm/sas");
  }

  @Test
  @Order(11)
  void deleteWidgetInstanceEnforcesNotFoundThenEditAccess() throws Exception {
    var board = createDashboard(USER_1, "Delete-instance board");
    var body = json.createObjectNode()
        .put("name", "doomed")
        .put("definitionName", "ERM Agreements")
        .put("definitionVersion", "1.0");
    body.putObject("owner").put("id", board);
    var instance = read(mockMvc.perform(as(post("/servint/widgets/instances"), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
        .andExpect(status().isCreated()).andReturn()).path("id").asText();

    // Unknown instance -> 404, checked before any access decision.
    mockMvc.perform(as(delete("/servint/widgets/instances/" + UUID.randomUUID()), USER_1))
        .andExpect(status().isNotFound());

    // A user without edit on the owning dashboard -> 403.
    mockMvc.perform(as(delete("/servint/widgets/instances/" + instance), USER_2))
        .andExpect(status().isForbidden());

    // The owner (manage ⊃ edit) deletes -> 204, and the instance is gone.
    mockMvc.perform(as(delete("/servint/widgets/instances/" + instance), USER_1))
        .andExpect(status().isNoContent());
    mockMvc.perform(as(get("/servint/widgets/instances/" + instance), USER_1))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(12)
  void updateDashboardAppliesNameAndDescriptionWithAdminOverride() throws Exception {
    var board = createDashboard(USER_1, "Update board");
    var update = json.createObjectNode().put("name", "renamed").put("description", "desc-set");
    var updated = read(mockMvc.perform(as(put("/servint/dashboard/" + board), USER_1)
            .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
        .andExpect(status().isOk()).andReturn());
    assertThat(updated.path("name").asText()).isEqualTo("renamed");
    assertThat(updated.path("description").asText()).isEqualTo("desc-set");

    // Admin authority bypasses per-dashboard access; an unknown dashboard is then a true 404.
    mockMvc.perform(asAdmin(put("/servint/dashboard/" + UUID.randomUUID()), USER_3)
            .contentType(MediaType.APPLICATION_JSON).content(update.toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(13)
  void editDashboardUsersResolvesEveryAccessBindingShape() throws Exception {
    var board = createDashboard(USER_1, "resolveAccess board");
    // A real refdata id in the access category: created lazily on the manage grant above.
    var manageId = jdbcTemplate.queryForObject(
        "select v.rdv_id from " + SCHEMA + ".refdata_value v "
            + "join " + SCHEMA + ".refdata_category c on v.rdv_owner = c.rdc_id "
            + "where c.rdc_description = 'DashboardAccess.Access' and v.rdv_value = 'manage'",
        String.class);
    var user4 = UUID.randomUUID().toString();

    var items = json.createArrayNode();
    // {id:...} binding -> resolved by refdata id.
    var byId = items.addObject();
    byId.putObject("user").put("id", USER_2);
    byId.putObject("access").put("id", manageId);
    // {value:...} binding -> resolved by refdata value within the access category.
    var byValue = items.addObject();
    byValue.putObject("user").put("id", USER_3);
    byValue.putObject("access").put("value", "manage");
    // access omitted -> null binding, a null access object (legacy allows it).
    items.addObject().putObject("user").put("id", user4);

    var users = postUsers(board, USER_1, items);

    String ownAccessId = null;
    for (var access : users) {
      var uid = access.path("user").path("id").asText();
      if (uid.equals(USER_2) || uid.equals(USER_3)) {
        assertThat(access.path("access").path("value").asText()).isEqualTo("manage");
      }
      if (uid.equals(user4)) {
        assertThat(access.path("access").isMissingNode() || access.path("access").isNull()).isTrue();
      }
      if (uid.equals(USER_1)) {
        ownAccessId = access.path("id").asText();
      }
    }
    assertThat(ownAccessId).isNotNull();

    // id-bearing edge cases, all ignored per legacy contract (no 4xx/5xx):
    // (1) an id referencing a nonexistent access object -> silently skipped;
    // (2) an item carrying the caller's OWN access id -> refused (own access
    //     "can not currently be changed").
    var edges = json.createArrayNode();
    var ghost = edges.addObject();
    ghost.put("id", UUID.randomUUID().toString());
    ghost.putObject("user").put("id", USER_2);
    ghost.putObject("access").put("value", "view");
    var own = edges.addObject();
    own.put("id", ownAccessId);
    own.putObject("user").put("id", USER_1);
    own.putObject("access").put("value", "view");
    var afterEdges = postUsers(board, USER_1, edges);
    for (var access : afterEdges) {
      if (access.path("user").path("id").asText().equals(USER_1)) {
        // the caller's own access is unchanged (still manage)
        assertThat(access.path("access").path("value").asText()).isEqualTo("manage");
      }
    }
  }

  @Test
  @Order(14)
  void hasAccessRejectsUnknownDesiredLevel() {
    // USER_1 holds manage on dash1, so accessLevel is non-null and the switch
    // reaches its default arm; an unrecognized desired level is denied. No HTTP
    // endpoint passes an arbitrary level, so this covers the arm directly — and
    // the service reads the DB via JPA, so it must run inside the tenant's
    // FolioExecutionContext for the schema search-path to resolve.
    var okapiHeaders = java.util.Map.<String, java.util.Collection<String>>of(
        org.folio.spring.integration.XOkapiHeaders.TENANT, java.util.List.of(TENANT));
    try (var ignored = new org.folio.spring.scope.FolioExecutionContextSetter(
        moduleMetadata, okapiHeaders)) {
      assertThat(dashboardService.hasAccess("bogus", dash1, USER_1)).isFalse();
      // Sanity: the same holder resolves the known levels as granted.
      assertThat(dashboardService.hasAccess("manage", dash1, USER_1)).isTrue();
      assertThat(dashboardService.hasAccess("view", dash1, USER_1)).isTrue();
    }
  }
}
