package org.folio.servint.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.servint.domain.dto.WidgetDefinitionDto;
import org.folio.servint.mapper.WidgetMapper;
import org.folio.servint.repository.WidgetDefinitionRepository;
import org.folio.servint.rest.resource.DashboardDefinitionsApi;
import org.folio.servint.web.KiwtListing;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * The module's own implementation of the `dashboard` Okapi interface: GET
 * /dashboard/definitions serves the local WidgetDefinition catalog with no
 * permissions — the endpoint federation harvests from every implementor.
 */
@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardDefinitionsController implements DashboardDefinitionsApi {

  private final WidgetDefinitionRepository widgetDefinitions;
  private final WidgetMapper mapper;
  private final KiwtListing kiwt;

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public ResponseEntity<List<WidgetDefinitionDto>> serveDashboardDefinitions() {
    return (ResponseEntity) kiwt.list(widgetDefinitions, null, null, null, null, null, null, null,
        null, null, mapper::toDto);
  }
}
