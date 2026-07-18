package org.folio.servint.repository;

import java.time.Instant;
import java.util.List;
import org.folio.servint.domain.entity.DbKeyPair;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DbKeyPairRepository extends JpaRepository<DbKeyPair, String> {

  /** Legacy selection: valid now, earliest availableFrom first. */
  @Query("select kp from DbKeyPair kp where kp.usage = :usage"
      + " and kp.availableFrom <= :now and kp.expiresAt >= :now"
      + " order by kp.availableFrom asc")
  List<DbKeyPair> findValidByUsage(@Param("usage") String usage, @Param("now") Instant now);
}
