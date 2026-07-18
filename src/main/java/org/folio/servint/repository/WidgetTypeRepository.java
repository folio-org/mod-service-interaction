package org.folio.servint.repository;

import java.util.List;
import java.util.Optional;
import org.folio.servint.domain.entity.WidgetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WidgetTypeRepository
    extends JpaRepository<WidgetType, String>, JpaSpecificationExecutor<WidgetType> {

  List<WidgetType> findByName(String name);

  Optional<WidgetType> findByNameAndTypeVersion(String name, String typeVersion);
}
