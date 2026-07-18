package org.folio.servint.controller;

import lombok.RequiredArgsConstructor;
import org.folio.servint.domain.dto.AdminResultDto;
import org.folio.servint.rest.resource.AdminApi;
import org.folio.servint.service.dashboard.DashboardService;
import org.folio.servint.service.widget.WidgetTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the legacy AdminController: maintenance actions dispatched by
 * path segment, each answering {status: "OK"}.
 */
@RestController
@RequiredArgsConstructor
public class AdminController implements AdminApi {

  private final WidgetTypeService widgetTypeService;
  private final DashboardService dashboardService;

  @Override
  public ResponseEntity<AdminResultDto> executeAdminAction(String action) {
    switch (action) {
      case "triggerTypeImport" -> widgetTypeService.triggerTypeImport(false);
      case "triggerTypeImportClean" -> widgetTypeService.triggerTypeImport(true);
      case "ensureDisplayData" -> dashboardService.ensureDisplayData();
      default -> {
        return ResponseEntity.notFound().build();
      }
    }
    var result = new AdminResultDto();
    result.setStatus("OK");
    return ResponseEntity.ok(result);
  }
}
