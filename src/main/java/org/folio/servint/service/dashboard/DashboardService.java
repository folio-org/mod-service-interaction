package org.folio.servint.service.dashboard;

import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.servint.domain.dto.DashboardAccessDto;
import org.folio.servint.domain.dto.DashboardDto;
import org.folio.servint.domain.entity.Dashboard;
import org.folio.servint.domain.entity.DashboardAccess;
import org.folio.servint.domain.entity.DashboardDisplayData;
import org.folio.servint.domain.entity.ExternalUser;
import org.folio.servint.domain.entity.RefdataValue;
import org.folio.servint.repository.DashboardAccessRepository;
import org.folio.servint.repository.DashboardDisplayDataRepository;
import org.folio.servint.repository.DashboardRepository;
import org.folio.servint.service.refdata.RefdataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Port of the legacy DashboardService. The access hierarchy, the bulk-update
 * ignore rules, and the single-default invariant are the wire contract
 * (dashboard-access-control / user-ordering / user-management requirements)
 * — change nothing without a spec change. Bulk updates run one transaction
 * per item like the legacy withNewTransaction blocks.
 */
@Log4j2
@Service
public class DashboardService {

  public static final String CAT_ACCESS = "DashboardAccess.Access";
  public static final String ADMIN_AUTHORITY = "servint.dashboards.admin.allops";

  private final DashboardRepository dashboards;
  private final DashboardAccessRepository accessObjects;
  private final DashboardDisplayDataRepository displayData;
  private final ExternalUserService externalUsers;
  private final RefdataService refdata;
  private final TransactionTemplate itemTx;

  public DashboardService(DashboardRepository dashboards,
                          DashboardAccessRepository accessObjects,
                          DashboardDisplayDataRepository displayData,
                          ExternalUserService externalUsers,
                          RefdataService refdata,
                          PlatformTransactionManager transactionManager) {
    this.dashboards = dashboards;
    this.accessObjects = accessObjects;
    this.displayData = displayData;
    this.externalUsers = externalUsers;
    this.refdata = refdata;
    this.itemTx = new TransactionTemplate(transactionManager);
    this.itemTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional
  public Dashboard createDashboard(DashboardDto data, ExternalUser user, boolean defaultUserDashboard) {
    var dashboard = new Dashboard();
    dashboard.setName(data.getName());
    dashboard.setDescription(data.getDescription());
    dashboards.saveAndFlush(dashboard);

    var access = new DashboardAccess();
    access.setDashboard(dashboard);
    access.setUser(user);
    access.setAccess(refdata.lookupOrCreate(CAT_ACCESS, "Manage", "manage"));
    access.setUserDashboardWeight((int) countUserDashboards(user));
    access.setDefaultUserDashboard(defaultUserDashboard);
    accessObjects.saveAndFlush(access);

    var ddd = new DashboardDisplayData();
    ddd.setDashId(dashboard.getId());
    displayData.saveAndFlush(ddd);

    return dashboard;
  }

  @Transactional
  public Dashboard createDefaultDashboard(ExternalUser user) {
    var data = new DashboardDto();
    data.setName("My dashboard");
    return createDashboard(data, user, true);
  }

  public long countUserDashboards(ExternalUser user) {
    return accessObjects.countDistinctDashboardsByUserId(user.getId());
  }

  public String accessLevel(String dashboardId, String userId) {
    return accessObjects.findAccessValue(dashboardId, userId).orElse(null);
  }

  public boolean hasAccess(String desiredAccessLevel, String dashboardId, String userId) {
    var accessLevel = accessLevel(dashboardId, userId);
    if (accessLevel == null) {
      return false;
    }
    return switch (desiredAccessLevel) {
      case "view" -> accessLevel.equals("view") || hasAccess("edit", dashboardId, userId);
      case "edit" -> accessLevel.equals("edit") || hasAccess("manage", dashboardId, userId);
      case "manage" -> accessLevel.equals("manage");
      default -> {
        log.error("Cannot declare access for unknown access level {}", desiredAccessLevel);
        yield false;
      }
    };
  }

  @Transactional
  public void deleteAccessObjects(String dashboardId) {
    accessObjects.deleteByDashboardId(dashboardId);
  }

  @Transactional
  public void deleteDisplayDataObject(String dashboardId) {
    displayData.deleteByDashId(dashboardId);
  }

  @Transactional
  public void ensureDisplayData() {
    for (var dashId : dashboards.findIdsWithoutDisplayData()) {
      log.debug("Found dashboard without display data ({}), creating.", dashId);
      var ddd = new DashboardDisplayData();
      ddd.setDashId(dashId);
      displayData.saveAndFlush(ddd);
    }
  }

  /**
   * Legacy updateAccessToDashboard: per-item add/update/delete of a
   * dashboard's access objects. Items touching the caller's own access are
   * ignored, creations for already-granted users are ignored, edits may
   * change ONLY the access level, and dashboard-id mismatches are ignored.
   */
  public void updateAccessToDashboard(String dashboardId, List<Map<String, Object>> userAccess,
                                      String currentUserId) {
    var dash = dashboards.findById(dashboardId).orElse(null);
    for (var access : userAccess) {
      itemTx.executeWithoutResult(status -> {
        var id = (String) access.get("id");
        if (id == null) {
          createDashboardAccess(dash, dashboardId, access, currentUserId);
        } else {
          updateDashboardAccess(id, dashboardId, access, currentUserId);
        }
      });
    }
  }

  /** id-less item: grant new access unless it targets the caller or an already-granted user. */
  private void createDashboardAccess(Dashboard dash, String dashboardId,
                                     Map<String, Object> access, String currentUserId) {
    var userMap = asMap(access.get("user"));
    var userId = userMap == null ? null : (String) userMap.get("id");
    if (userId != null && userId.equals(currentUserId)) {
      log.warn("DashboardAccess can not currently be changed for the currently logged in user");
    }
    if (hasAccess("view", dashboardId, userId)) {
      log.warn("Ignoring DashboardAccess creation request since a DashboardAccess object "
          + "already exists for this user ({})", userId);
      return;
    }
    var user = externalUsers.resolveUser(userId);
    var dashboardCount = countUserDashboards(user);
    var created = new DashboardAccess();
    created.setUser(user);
    created.setDashboard(dash);
    created.setAccess(resolveAccess(access.get("access")));
    created.setUserDashboardWeight((int) dashboardCount);
    created.setDefaultUserDashboard(dashboardCount == 0);
    accessObjects.saveAndFlush(created);
  }

  /** id-bearing item: delete, or change ONLY the access level; the caller's own and mismatches are ignored. */
  private void updateDashboardAccess(String id, String dashboardId,
                                     Map<String, Object> access, String currentUserId) {
    var existing = accessObjects.findById(id).orElse(null);
    if (existing == null) {
      return;
    }
    if (existing.getUser().getId().equals(currentUserId)) {
      log.warn("DashboardAccess can not currently be changed for the currently logged in user");
    } else if (!existing.getDashboard().getId().equals(dashboardId)) {
      log.warn("Dashboard access object ({}) dashboard id mismatch. Expected {}. "
          + "Ignoring any requested changes", id, dashboardId);
    } else if (Boolean.TRUE.equals(access.get("_delete"))) {
      accessObjects.delete(existing);
    } else {
      var previousAccessId = existing.getAccess() == null ? null : existing.getAccess().getId();
      var newAccess = resolveAccess(access.get("access"));
      if (newAccess != null && !newAccess.getId().equals(previousAccessId)) {
        existing.setAccess(newAccess);
        accessObjects.saveAndFlush(existing);
      }
    }
  }

  /**
   * Legacy updateUserDashboards: per-item ordering update of the caller's
   * own access objects. Only userDashboardWeight and a false-to-true
   * defaultUserDashboard transition apply; electing a new default clears
   * the flag on the caller's other access objects.
   */
  public void updateUserDashboards(List<DashboardAccessDto> userAccess, String currentUserId) {
    for (var access : userAccess) {
      itemTx.executeWithoutResult(status -> {
        if (!ownsUpdatableItem(access, currentUserId)) {
          log.warn("DashboardAccess item ignored by updateUserDashboards ({})", access.getId());
          return;
        }
        var existing = accessObjects.findById(access.getId()).orElse(null);
        if (existing == null) {
          log.warn("DashboardAccess can not be created through updateUserDashboards, ignoring.");
          return;
        }
        applyOrdering(existing, access);
      });
    }
  }

  /** The item is the caller's own, existing access object (never creates). */
  private static boolean ownsUpdatableItem(DashboardAccessDto access, String currentUserId) {
    return access.getId() != null && access.getUser() != null && access.getUser().getId() != null
        && access.getUser().getId().equals(currentUserId);
  }

  /** Applies weight + a false-to-true default transition, clearing the caller's other defaults. */
  private void applyOrdering(DashboardAccess existing, DashboardAccessDto access) {
    var previousWeight = existing.getUserDashboardWeight();
    var previousDefault = existing.isDefaultUserDashboard();

    existing.setUserDashboardWeight(access.getUserDashboardWeight());
    // Never transitions true -> false; a NEW default clears the others below.
    existing.setDefaultUserDashboard(previousDefault
        || Boolean.TRUE.equals(access.getDefaultUserDashboard()));

    if (Boolean.TRUE.equals(access.getDefaultUserDashboard()) && !previousDefault) {
      clearOtherDefaults(existing);
    }

    var weightChanged = previousWeight == null
        ? existing.getUserDashboardWeight() != null
        : !previousWeight.equals(existing.getUserDashboardWeight());
    if (weightChanged || previousDefault != existing.isDefaultUserDashboard()) {
      accessObjects.saveAndFlush(existing);
    }
  }

  /** Clears the default flag on every OTHER access object of the same user. */
  private void clearOtherDefaults(DashboardAccess elected) {
    for (var other : accessObjects.findByUserId(elected.getUser().getId())) {
      if (!other.getId().equals(elected.getId()) && other.isDefaultUserDashboard()) {
        other.setDefaultUserDashboard(false);
        accessObjects.saveAndFlush(other);
      }
    }
  }

  /** Legacy kiwt refdata binding: an {id}/{value} map, or a bare id-or-value string. */
  private RefdataValue resolveAccess(Object binding) {
    if (binding == null) {
      return null;
    }
    if (binding instanceof Map<?, ?> map) {
      var id = (String) map.get("id");
      if (id != null) {
        return refdata.findById(id).orElse(null);
      }
      var value = (String) map.get("value");
      return value == null ? null : refdata.find(CAT_ACCESS, value).orElse(null);
    }
    var raw = binding.toString();
    return refdata.findById(raw).orElseGet(() -> refdata.find(CAT_ACCESS, raw).orElse(null));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : null;
  }
}
