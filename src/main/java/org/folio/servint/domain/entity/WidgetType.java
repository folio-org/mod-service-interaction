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
 * Legacy WidgetType (table widget_type, cols wtype_*). schema is the JSON
 * schema as a persisted string; (name, typeVersion) is unique.
 */
@Entity
@Table(name = "widget_type")
@Getter
@Setter
public class WidgetType {

  @Id
  @Column(name = "wtype_id", length = 36)
  private String id;

  @Version
  @Column(name = "wtype_version", nullable = false)
  private Long version;

  @Column(name = "wtype_name")
  private String name;

  @Column(name = "wtype_type_version", length = 36)
  private String typeVersion;

  @Column(name = "wtype_schema")
  private String schema;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
