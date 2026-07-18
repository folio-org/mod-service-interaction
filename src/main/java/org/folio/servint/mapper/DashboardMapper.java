package org.folio.servint.mapper;

import org.folio.servint.domain.dto.DashboardAccessDto;
import org.folio.servint.domain.dto.DashboardAccessDtoUser;
import org.folio.servint.domain.dto.DashboardDisplayDataDto;
import org.folio.servint.domain.dto.DashboardDto;
import org.folio.servint.domain.dto.DashboardDto1;
import org.folio.servint.domain.dto.DashboardDtoWidgetsInner;
import org.folio.servint.domain.entity.Dashboard;
import org.folio.servint.domain.entity.DashboardAccess;
import org.folio.servint.domain.entity.DashboardDisplayData;
import org.folio.servint.domain.entity.ExternalUser;
import org.folio.servint.domain.entity.WidgetInstance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Wire rendering per the legacy gson views: dashboards render widgets as
 * {id, weight, name} summaries; access objects expand the access refdata
 * always and the dashboard everywhere EXCEPT the dashboard-users actions.
 */
@Mapper(componentModel = "spring", uses = NumgenMapper.class)
public interface DashboardMapper {

  DashboardDto toDto(Dashboard entity);

  DashboardDto1 toDto1(Dashboard entity);

  DashboardDtoWidgetsInner toWidgetSummary(WidgetInstance entity);

  DashboardAccessDtoUser toDto(ExternalUser entity);

  DashboardDisplayDataDto toDto(DashboardDisplayData entity);

  /** Full render: dashboard expanded (my-dashboards, editUserDashboards). */
  DashboardAccessDto toDto(DashboardAccess entity);

  /**
   * Users-actions render (get/editDashboardUsers): the dashboard is not
   * expanded — it renders as an id-only reference, like the legacy gson
   * default for a non-expanded association (M4-verified).
   */
  @Named("accessNoDashboard")
  @Mapping(target = "dashboard", qualifiedByName = "dashboardIdStub")
  DashboardAccessDto toDtoNoDashboard(DashboardAccess entity);

  @Named("dashboardIdStub")
  default DashboardDto1 toIdStub(Dashboard entity) {
    if (entity == null) {
      return null;
    }
    var stub = new DashboardDto1();
    stub.setId(entity.getId());
    stub.setWidgets(null);
    return stub;
  }

  /** Legacy Grails date render: second-precision UTC (yyyy-MM-dd'T'HH:mm:ss'Z'). */
  default String map(java.time.Instant instant) {
    return instant == null ? null
        : instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
  }
}
