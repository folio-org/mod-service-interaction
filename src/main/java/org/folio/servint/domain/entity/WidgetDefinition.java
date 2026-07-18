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
 * Legacy WidgetDefinition (table widget_definition, cols wdef_*). definition
 * is the JSON body as a persisted string; (name, definitionVersion) is
 * unique.
 */
@Entity
@Table(name = "widget_definition")
@Getter
@Setter
public class WidgetDefinition {

  @Id
  @Column(name = "wdef_id", length = 36)
  private String id;

  @Version
  @Column(name = "wdef_version", nullable = false)
  private Long version;

  @Column(name = "wdef_name")
  private String name;

  @Column(name = "wdef_definition_version", length = 36)
  private String definitionVersion;

  @Column(name = "wdef_definition")
  private String definition;

  @Column(name = "wdef_type_name")
  private String typeName;

  @Column(name = "wdef_type_version", length = 36)
  private String typeVersion;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
