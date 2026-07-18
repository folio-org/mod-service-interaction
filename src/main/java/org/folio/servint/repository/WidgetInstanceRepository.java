package org.folio.servint.repository;

import java.util.Optional;
import org.folio.servint.domain.entity.WidgetInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WidgetInstanceRepository
    extends JpaRepository<WidgetInstance, String>, JpaSpecificationExecutor<WidgetInstance> {

  @Query("select max(wi.weight) from WidgetInstance wi where wi.owner.id = :ownerId")
  Optional<Integer> findMaxWeightByOwnerId(@Param("ownerId") String ownerId);
}
