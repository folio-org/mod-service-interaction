package org.folio.servint.repository;

import org.folio.servint.domain.entity.WidgetDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WidgetDefinitionRepository
    extends JpaRepository<WidgetDefinition, String>, JpaSpecificationExecutor<WidgetDefinition> {
}
