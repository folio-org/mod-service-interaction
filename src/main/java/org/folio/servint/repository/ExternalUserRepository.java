package org.folio.servint.repository;

import org.folio.servint.domain.entity.ExternalUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalUserRepository extends JpaRepository<ExternalUser, String> {
}
