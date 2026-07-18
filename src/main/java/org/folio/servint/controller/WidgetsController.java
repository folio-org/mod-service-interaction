package org.folio.servint.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.servint.domain.dto.WidgetDefinitionDto;
import org.folio.servint.domain.dto.WidgetInstanceDto;
import org.folio.servint.domain.dto.WidgetTypeDto;
import org.folio.servint.domain.entity.WidgetInstance;
import org.folio.servint.mapper.WidgetMapper;
import org.folio.servint.repository.DashboardRepository;
import org.folio.servint.repository.WidgetDefinitionRepository;
import org.folio.servint.repository.WidgetInstanceRepository;
import org.folio.servint.repository.WidgetTypeRepository;
import org.folio.servint.rest.resource.WidgetsApi;
import org.folio.servint.service.dashboard.DashboardService;
import org.folio.servint.service.widget.WidgetDefinitionService;
import org.folio.servint.web.KiwtListing;
import org.folio.servint.web.OkapiAuthorityService;
import org.folio.spring.FolioExecutionContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the legacy Widget{Definition,Instance,Type}Controllers. Instance
 * authorization derives from the OWNING dashboard's access level: view to
 * read, edit to create/update/delete, admin authority bypasses all.
 */
@RestController
@RequiredArgsConstructor
@Transactional
public class WidgetsController implements WidgetsApi {

  private final WidgetDefinitionRepository widgetDefinitions;
  private final WidgetInstanceRepository widgetInstances;
  private final WidgetTypeRepository widgetTypes;
  private final DashboardRepository dashboards;
  private final DashboardService dashboardService;
  private final WidgetDefinitionService widgetDefinitionService;
  private final OkapiAuthorityService authorities;
  private final FolioExecutionContext folioExecutionContext;
  private final WidgetMapper mapper;
  private final KiwtListing kiwt;

  private String patronId() {
    return folioExecutionContext.getUserId().toString();
  }

  private boolean hasAdminPerm() {
    return authorities.hasAuthority(DashboardService.ADMIN_AUTHORITY);
  }

  private boolean hasAccessToDashboard(String desiredAccessLevel, String dashboardId) {
    return dashboardService.hasAccess(desiredAccessLevel, dashboardId, patronId());
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<WidgetDefinitionDto>> listWidgetDefinitions(List<String> filters,
      List<String> match, String term, List<String> sort, Integer perPage, Integer max, Integer page,
      Integer offset, Boolean stats) {
    return (ResponseEntity) kiwt.list(widgetDefinitions, filters, match, term, sort, perPage, max,
        page, offset, stats, mapper::toDto);
  }

  @Override
  public ResponseEntity<List<WidgetDefinitionDto>> listGlobalWidgetDefinitions(String name,
      String nameLike, String version) {
    var federated = widgetDefinitionService.fetchDefinitions(name, nameLike, version);
    return ResponseEntity.ok(federated.stream().map(mapper::toDto).toList());
  }

  @Override
  public ResponseEntity<WidgetDefinitionDto> getWidgetDefinition(String id) {
    return widgetDefinitions.findById(id)
        .map(definition -> ResponseEntity.ok(mapper.toDto(definition)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<WidgetInstanceDto>> listAllWidgetInstances(List<String> filters,
      List<String> match, String term, List<String> sort, Integer perPage, Integer max, Integer page,
      Integer offset, Boolean stats) {
    if (!hasAdminPerm()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return (ResponseEntity) kiwt.list(widgetInstances, filters, match, term, sort, perPage, max, page,
        offset, stats, mapper::toDto);
  }

  @Override
  public ResponseEntity<WidgetInstanceDto> createWidgetInstance(WidgetInstanceDto widgetInstanceDto) {
    var ownerId = widgetInstanceDto.getOwner() == null ? null : widgetInstanceDto.getOwner().getId();
    var dashboard = ownerId == null ? null : dashboards.findById(ownerId).orElse(null);
    if (dashboard == null) {
      return ResponseEntity.notFound().build();
    }
    if (!hasAccessToDashboard("edit", ownerId) && !hasAdminPerm()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    var instance = new WidgetInstance();
    instance.setOwner(dashboard);
    bind(instance, widgetInstanceDto);
    assignWeightIfAbsent(instance);
    widgetInstances.saveAndFlush(instance);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(instance));
  }

  @Override
  public ResponseEntity<WidgetInstanceDto> getWidgetInstance(String id) {
    var instance = widgetInstances.findById(id).orElse(null);
    if (instance == null) {
      return ResponseEntity.notFound().build();
    }
    if (!hasAccessToDashboard("view", instance.getOwner().getId()) && !hasAdminPerm()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return ResponseEntity.ok(mapper.toDto(instance));
  }

  @Override
  public ResponseEntity<WidgetInstanceDto> updateWidgetInstance(String id,
      WidgetInstanceDto widgetInstanceDto) {
    var instance = widgetInstances.findById(id).orElse(null);
    if (instance == null) {
      return ResponseEntity.notFound().build();
    }
    if (!hasAccessToDashboard("edit", instance.getOwner().getId()) && !hasAdminPerm()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    bind(instance, widgetInstanceDto);
    assignWeightIfAbsent(instance);
    widgetInstances.saveAndFlush(instance);
    return ResponseEntity.ok(mapper.toDto(instance));
  }

  @Override
  public ResponseEntity<Void> deleteWidgetInstance(String id) {
    var instance = widgetInstances.findById(id).orElse(null);
    if (instance == null) {
      return ResponseEntity.notFound().build();
    }
    if (!hasAccessToDashboard("edit", instance.getOwner().getId()) && !hasAdminPerm()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    widgetInstances.delete(instance);
    return ResponseEntity.noContent().build();
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<WidgetTypeDto>> listWidgetTypes(List<String> filters, List<String> match,
      String term, List<String> sort, Integer perPage, Integer max, Integer page, Integer offset,
      Boolean stats) {
    return (ResponseEntity) kiwt.list(widgetTypes, filters, match, term, sort, perPage, max, page,
        offset, stats, mapper::toDto);
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<WidgetInstanceDto>> getDashboardWidgets(String id) {
    if (!hasAccessToDashboard("view", id) && !hasAdminPerm()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return (ResponseEntity) kiwt.list(widgetInstances, List.of("owner.id==" + id),
        null, null, null, null, null, null, null, null, mapper::toDto);
  }

  /** Legacy kiwt binding: requests carry the FLAT definitionName/definitionVersion. */
  private void bind(WidgetInstance instance, WidgetInstanceDto dto) {
    if (dto.getName() != null) {
      instance.setName(dto.getName());
    }
    if (dto.getWeight() != null) {
      instance.setWeight(dto.getWeight());
    }
    if (dto.getDefinitionName() != null) {
      instance.setDefinitionName(dto.getDefinitionName());
    }
    if (dto.getDefinitionVersion() != null) {
      instance.setDefinitionVersion(dto.getDefinitionVersion());
    }
    if (dto.getConfiguration() != null) {
      instance.setConfiguration(dto.getConfiguration());
    }
  }

  /** Legacy beforeValidate: a null weight becomes max(owner's weights) + 1, or 0. */
  private void assignWeightIfAbsent(WidgetInstance instance) {
    if (instance.getWeight() == null) {
      instance.setWeight(widgetInstances.findMaxWeightByOwnerId(instance.getOwner().getId())
          .map(max -> max + 1).orElse(0));
    }
  }
}
