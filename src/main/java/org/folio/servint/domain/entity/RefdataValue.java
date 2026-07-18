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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Legacy web-toolkit refdata value (table refdata_value, cols rdv_*).
 * Values are normalized (lowercase, whitespace to underscore) like the
 * legacy RefdataValue.normValue.
 */
@Entity
@Table(name = "refdata_value")
@Getter
@Setter
public class RefdataValue {

  @Id
  @Column(name = "rdv_id", length = 36)
  private String id;

  @Version
  @Column(name = "rdv_version", nullable = false)
  private Long version;

  @Column(name = "rdv_value", nullable = false)
  private String value;

  @Column(name = "rdv_label", nullable = false)
  private String label;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "rdv_owner", nullable = false)
  private RefdataCategory owner;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
