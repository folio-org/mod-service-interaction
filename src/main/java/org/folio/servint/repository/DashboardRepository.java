package org.folio.servint.repository;

import java.util.List;
import org.folio.servint.domain.entity.Dashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface DashboardRepository
    extends JpaRepository<Dashboard, String>, JpaSpecificationExecutor<Dashboard> {

  @Query("select d.id from Dashboard d where not exists "
      + "(select 1 from DashboardDisplayData ddd where ddd.dashId = d.id)")
  List<String> findIdsWithoutDisplayData();
}
