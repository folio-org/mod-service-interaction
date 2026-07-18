package org.folio.servint.repository;

import java.util.Optional;
import org.folio.servint.domain.entity.DashboardDisplayData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardDisplayDataRepository extends JpaRepository<DashboardDisplayData, String> {

  Optional<DashboardDisplayData> findByDashId(String dashId);

  @Modifying
  @Query("delete from DashboardDisplayData ddd where ddd.dashId = :dashId")
  void deleteByDashId(@Param("dashId") String dashId);
}
