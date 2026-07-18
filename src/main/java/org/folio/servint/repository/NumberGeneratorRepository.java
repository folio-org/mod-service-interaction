package org.folio.servint.repository;

import java.util.Optional;
import org.folio.servint.domain.entity.NumberGenerator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NumberGeneratorRepository
    extends JpaRepository<NumberGenerator, String>, JpaSpecificationExecutor<NumberGenerator> {

  Optional<NumberGenerator> findByCode(String code);

  /**
   * Race-free auto-creation for concurrent first use (review F-05): the loser
   * of a concurrent insert must fall through to the locked re-read instead of
   * dying on NumberGeneratorUniqueCode (ng_code).
   */
  @Modifying
  @Query(value = "insert into number_generator (ng_id, ng_version, ng_code, ng_name) "
      + "values (:id, 0, :code, :code) on conflict (ng_code) do nothing", nativeQuery = true)
  int insertIfAbsent(@Param("id") String id, @Param("code") String code);
}
