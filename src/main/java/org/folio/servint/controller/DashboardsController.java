package org.folio.servint.controller;

import java.util.List;
import java.util.Map;
import org.folio.servint.domain.dto.DashboardAccessDto;
import org.folio.servint.domain.dto.DashboardDisplayDataDto;
import org.folio.servint.domain.dto.DashboardDto;
import org.folio.servint.domain.dto.MyAccessResultDto;
import org.folio.servint.mapper.DashboardMapper;
import org.folio.servint.repository.DashboardAccessRepository;
import org.folio.servint.repository.DashboardDisplayDataRepository;
import org.folio.servint.repository.DashboardRepository;
import org.folio.servint.rest.resource.DashboardsApi;
import org.folio.servint.service.dashboard.DashboardService;
import org.folio.servint.service.dashboard.ExternalUserService;
import org.folio.servint.web.KiwtListing;
import org.folio.servint.web.LegacyValidationException;
import org.folio.servint.web.OkapiAuthorityService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the legacy DashboardController. Access rules: view < edit <
 * manage per DashboardAccess row, with the admin authority bypassing every
 * check. Missing dashboards answer 403 before 404 for non-admin callers,
 * exactly like the legacy access-first checks.
 *
 * <p>Transactions are per-method, not class-level (F-17): the bulk-edit
 * endpoints delegate to per-item REQUIRES_NEW loops in DashboardService,
 * and an outer controller transaction would pin a second pooled connection
 * per request — deadlocking the Hikari pool at pool-size concurrency.
 */
@RestController
public class DashboardsController implements DashboardsApi {

  private final DashboardRepository dashboards;
  private final DashboardAccessRepository accessObjects;
  private final DashboardDisplayDataRepository displayData;
  private final DashboardService dashboardService;
  private final ExternalUserService externalUsers;
  private final OkapiAuthorityService authorities;
  private final FolioExecutionContext folioExecutionContext;
  private final DashboardMapper mapper;
  private final KiwtListing kiwt;
  private final TransactionTemplate readTx;

  public DashboardsController(DashboardRepository dashboards,
                              DashboardAccessRepository accessObjects,
                              DashboardDisplayDataRepository displayData,
                              DashboardService dashboardService,
                              ExternalUserService externalUsers,
                              OkapiAuthorityService authorities,
                              FolioExecutionContext folioExecutionContext,
                              DashboardMapper mapper,
                              KiwtListing kiwt,
                              PlatformTransactionManager transactionManager) {
    this.dashboards = dashboards;
    this.accessObjects = accessObjects;
    this.displayData = displayData;
    this.dashboardService = dashboardService;
    this.externalUsers = externalUsers;
    this.authorities = authorities;
    this.folioExecutionContext = folioExecutionContext;
    this.mapper = mapper;
    this.kiwt = kiwt;
    // The bulk-edit endpoints re-list after their per-item loop through a
    // self-invocation the @Transactional proxy cannot intercept; this
    // template supplies the transaction the lazy DTO mapping needs.
    this.readTx = new TransactionTemplate(transactionManager);
  }

  private String patronId() {
    return folioExecutionContext.getUserId().toString();
  }

  private boolean hasAdminPerm() {
    return authorities.hasAuthority(DashboardService.ADMIN_AUTHORITY);
  }

  private boolean canView(String dashboardId) {
    return dashboardService.hasAccess("view", dashboardId, patronId()) || hasAdminPerm();
  }

  private boolean canEdit(String dashboardId) {
    return dashboardService.hasAccess("edit", dashboardId, patronId()) || hasAdminPerm();
  }

  private boolean canManage(String dashboardId) {
    return dashboardService.hasAccess("manage", dashboardId, patronId()) || hasAdminPerm();
  }

  @Override
  @Transactional
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<DashboardDto>> listAllDashboards(List<String> filters, List<String> match,
      String term, List<String> sort, Integer perPage, Integer max, Integer page, Integer offset,
      Boolean stats) {
    if (!hasAdminPerm()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return (ResponseEntity) kiwt.list(dashboards, filters, match, term, sort, perPage, max, page,
        offset, stats, mapper::toDto);
  }

  @Override
  @Transactional
  public ResponseEntity<DashboardDto> createDashboard(DashboardDto dashboardDto) {
    var user = externalUsers.resolveUser(patronId());
    var defaultUserDashboard = dashboardService.countUserDashboards(user) == 0;
    var dashboard = dashboardService.createDashboard(dashboardDto, user, defaultUserDashboard);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(dashboard));
  }

  @Override
  @Transactional
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<DashboardAccessDto>> getMyDashboards() {
    var user = externalUsers.resolveUser(patronId());
    if (dashboardService.countUserDashboards(user) == 0) {
      dashboardService.createDefaultDashboard(user);
    }
    return (ResponseEntity) kiwt.list(accessObjects, List.of("user.id==" + user.getId()),
        null, null, null, null, null, null, null, null, mapper::toDto);
  }

  @Override
  public ResponseEntity<List<DashboardAccessDto>> editUserDashboards(List<DashboardAccessDto> items) {
    var patronId = patronId();
    for (var access : items) {
      if (access.getId() == null) {
        return ResponseEntity.badRequest().build();
      }
      if (access.getUser() == null || access.getUser().getId() == null) {
        return ResponseEntity.badRequest().build();
      }
      if (!access.getUser().getId().equals(patronId)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }
    dashboardService.updateUserDashboards(items, patronId);
    return readTx.execute(status -> getMyDashboards());
  }

  @Override
  @Transactional
  public ResponseEntity<DashboardDto> getDashboard(String id) {
    if (!canView(id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return dashboards.findById(id)
        .map(dashboard -> ResponseEntity.ok(mapper.toDto(dashboard)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  @Transactional
  public ResponseEntity<DashboardDto> updateDashboard(String id, DashboardDto dashboardDto) {
    if (!canEdit(id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    var dashboard = dashboards.findById(id).orElse(null);
    if (dashboard == null) {
      return ResponseEntity.notFound().build();
    }
    if (dashboardDto.getName() != null) {
      dashboard.setName(dashboardDto.getName());
    }
    if (dashboardDto.getDescription() != null) {
      dashboard.setDescription(dashboardDto.getDescription());
    }
    dashboards.saveAndFlush(dashboard);
    return ResponseEntity.ok(mapper.toDto(dashboard));
  }

  @Override
  @Transactional
  public ResponseEntity<Void> deleteDashboard(String id) {
    if (!canManage(id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    var dashboard = dashboards.findById(id).orElse(null);
    if (dashboard == null) {
      return ResponseEntity.notFound().build();
    }
    dashboardService.deleteAccessObjects(id);
    dashboardService.deleteDisplayDataObject(id);
    dashboards.delete(dashboard);
    return ResponseEntity.noContent().build();
  }

  @Override
  @Transactional
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<DashboardAccessDto>> getDashboardUsers(String id) {
    if (!canView(id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return (ResponseEntity) kiwt.list(accessObjects, List.of("dashboard.id==" + id),
        null, null, null, null, null, null, null, null, mapper::toDtoNoDashboard);
  }

  @Override
  @SuppressWarnings("unchecked")
  public ResponseEntity<List<DashboardAccessDto>> editDashboardUsers(String id, List<Object> requestBody) {
    if (!canManage(id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    var items = requestBody.stream().map(item -> (Map<String, Object>) item).toList();
    dashboardService.updateAccessToDashboard(id, items, patronId());
    return readTx.execute(status -> getDashboardUsers(id));
  }

  @Override
  @Transactional
  public ResponseEntity<MyAccessResultDto> getMyAccess(String id) {
    if (!canView(id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    var result = new MyAccessResultDto();
    result.setAccess(dashboardService.accessLevel(id, patronId()));
    return ResponseEntity.ok(result);
  }

  @Override
  @Transactional
  public ResponseEntity<DashboardDisplayDataDto> getDisplayData(String id) {
    if (!canView(id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return displayData.findByDashId(id)
        .map(ddd -> ResponseEntity.ok(mapper.toDto(ddd)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  @Transactional
  public ResponseEntity<DashboardDisplayDataDto> updateDisplayData(String id,
      DashboardDisplayDataDto dashboardDisplayDataDto) {
    if (!canEdit(id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    // The path id is the authorized identity; a differing body dashId is a
    // cross-dashboard write and is rejected (F-09, deviation D-20 — the
    // inherited legacy behavior re-pointed the row at the body's dashboard).
    var bodyDashId = dashboardDisplayDataDto.getDashId();
    if (bodyDashId != null && !bodyDashId.equals(id)) {
      throw new LegacyValidationException("dashboard.id.mismatch",
          "Display-data dashId [" + bodyDashId + "] does not match the dashboard id ["
              + id + "] addressed by the request path");
    }
    var ddd = displayData.findByDashId(id).orElse(null);
    if (ddd == null) {
      return ResponseEntity.notFound().build();
    }
    if (dashboardDisplayDataDto.getLayoutData() != null) {
      ddd.setLayoutData(dashboardDisplayDataDto.getLayoutData());
    }
    displayData.saveAndFlush(ddd);
    return ResponseEntity.ok(mapper.toDto(ddd));
  }
}
