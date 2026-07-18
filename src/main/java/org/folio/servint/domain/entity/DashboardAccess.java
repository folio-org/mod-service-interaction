package org.folio.servint.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Legacy DashboardAccess (table dashboard_access, cols da_*). One row per
 * (dashboard, user); access is a DashboardAccess.Access refdata value
 * (view < edit < manage).
 */
@Entity
@Table(name = "dashboard_access")
@Getter
@Setter
public class DashboardAccess {

  @Id
  @Column(name = "da_id", length = 36)
  private String id;

  @Version
  @Column(name = "da_version", nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "da_dashboard_fk")
  private Dashboard dashboard;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "da_user_fk")
  private ExternalUser user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "da_access_fk")
  private RefdataValue access;

  @Column(name = "da_date_created")
  private Instant dateCreated;

  @Column(name = "da_user_dashboard_weight")
  private Integer userDashboardWeight;

  @Column(name = "da_default_user_dashboard", nullable = false)
  private boolean defaultUserDashboard = false;

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
    if (dateCreated == null) {
      dateCreated = Instant.now();
    }
  }
}
