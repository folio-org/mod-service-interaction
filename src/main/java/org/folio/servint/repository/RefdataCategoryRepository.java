package org.folio.servint.repository;

import java.util.Optional;
import org.folio.servint.domain.entity.RefdataCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RefdataCategoryRepository
    extends JpaRepository<RefdataCategory, String>, JpaSpecificationExecutor<RefdataCategory> {

  Optional<RefdataCategory> findByDesc(String description);
}
