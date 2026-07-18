package org.folio.servint.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Legacy web-toolkit refdata category (table refdata_category, cols rdc_* —
 * except the historical unprefixed "internal" column). ADR-007: bespoke JPA
 * over the unchanged legacy table shape.
 */
@Entity
@Table(name = "refdata_category")
@Getter
@Setter
public class RefdataCategory {

  @Id
  @Column(name = "rdc_id", length = 36)
  private String id;

  @Version
  @Column(name = "rdc_version", nullable = false)
  private Long version;

  /** Named desc like the legacy GORM property so kiwt filter paths match. */
  @Column(name = "rdc_description", nullable = false)
  private String desc;

  @Column(name = "internal", nullable = false)
  private boolean internal;

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("value")
  private List<RefdataValue> values = new ArrayList<>();

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
