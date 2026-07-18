package org.folio.servint.domain.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Legacy Dashboard (table dashboard, cols dshb_*). Widgets cascade like the
 * legacy all-delete-orphan mapping.
 */
@Entity
@Table(name = "dashboard")
@Getter
@Setter
public class Dashboard {

  @Id
  @Column(name = "dshb_id", length = 36)
  private String id;

  @Version
  @Column(name = "dshb_version", nullable = false)
  private Long version;

  @Column(name = "dshb_name")
  private String name;

  @Column(name = "dshb_description")
  private String description;

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<WidgetInstance> widgets = new ArrayList<>();

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
