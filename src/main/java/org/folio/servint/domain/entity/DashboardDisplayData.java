package org.folio.servint.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Legacy DashboardDisplayData (table dashboard_display_data, cols ddd_*).
 * dashId is deliberately NOT a foreign key, matching the legacy model.
 */
@Entity
@Table(name = "dashboard_display_data")
@Getter
@Setter
public class DashboardDisplayData {

  @Id
  @Column(name = "ddd_id", length = 36)
  private String id;

  @Version
  @Column(name = "ddd_version", nullable = false)
  private Long version;

  @Column(name = "ddd_dash_id", length = 36, nullable = false)
  private String dashId;

  @Column(name = "ddd_layout_data")
  private String layoutData;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
