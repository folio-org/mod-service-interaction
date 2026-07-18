package org.folio.servint.repository;

import java.util.Optional;
import org.folio.servint.domain.entity.RefdataValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RefdataValueRepository
    extends JpaRepository<RefdataValue, String>, JpaSpecificationExecutor<RefdataValue> {

  Optional<RefdataValue> findByOwnerDescAndValue(String ownerDescription, String value);
}
