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
 * Legacy WidgetInstance (table widget_instance, cols wins_*). The
 * instantiated definition is referenced by the flat
 * definitionName/definitionVersion pair — there is no FK to
 * WidgetDefinition, matching the legacy model.
 */
@Entity
@Table(name = "widget_instance")
@Getter
@Setter
public class WidgetInstance {

  @Id
  @Column(name = "wins_id", length = 36)
  private String id;

  @Version
  @Column(name = "wins_version", nullable = false)
  private Long version;

  @Column(name = "wins_name")
  private String name;

  @Column(name = "wins_weight")
  private Integer weight;

  @Column(name = "wins_definition_name")
  private String definitionName;

  @Column(name = "wins_definition_version", length = 36)
  private String definitionVersion;

  @Column(name = "wins_configuration")
  private String configuration;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "wins_owner_fk", nullable = false)
  private Dashboard owner;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
