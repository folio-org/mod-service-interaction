package org.folio.servint.repository;

import java.util.List;
import java.util.Optional;
import org.folio.servint.domain.entity.DashboardAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardAccessRepository
    extends JpaRepository<DashboardAccess, String>, JpaSpecificationExecutor<DashboardAccess> {

  /** Legacy countUserDashboards: COUNT(DISTINCT dashboard) for the user. */
  @Query("select count(distinct da.dashboard.id) from DashboardAccess da where da.user.id = :userId")
  long countDistinctDashboardsByUserId(@Param("userId") String userId);

  /** Legacy accessLevel: the access refdata VALUE for (dashboard, user). */
  @Query("select da.access.value from DashboardAccess da "
      + "where da.dashboard.id = :dashboardId and da.user.id = :userId")
  Optional<String> findAccessValue(@Param("dashboardId") String dashboardId, @Param("userId") String userId);

  List<DashboardAccess> findByUserId(String userId);

  @Modifying
  @Query("delete from DashboardAccess da where da.dashboard.id = :dashboardId")
  void deleteByDashboardId(@Param("dashboardId") String dashboardId);
}
