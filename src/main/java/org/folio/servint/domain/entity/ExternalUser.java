package org.folio.servint.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Reference stub for a FOLIO user (table external_user, cols eu_*). The id
 * is ASSIGNED — it IS the FOLIO user UUID, never generated locally.
 */
@Entity
@Table(name = "external_user")
@Getter
@Setter
public class ExternalUser {

  @Id
  @Column(name = "eu_id", length = 36)
  private String id;

  @Version
  @Column(name = "eu_version", nullable = false)
  private Long version;
}
