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

/** Legacy org.olf.numgen.NumberGenerator (table number_generator, cols ng_*). */
@Entity
@Table(name = "number_generator")
@Getter
@Setter
public class NumberGenerator {

  @Id
  @Column(name = "ng_id", length = 36)
  private String id;

  @Version
  @Column(name = "ng_version", nullable = false)
  private Long version;

  @Column(name = "ng_code", nullable = false, length = 36)
  private String code;

  @Column(name = "ng_name", nullable = false, length = 100)
  private String name;

  @Column(name = "ng_description", length = 256)
  private String description;

  @Column(name = "ng_default_seq_code", length = 36)
  private String defaultSequenceCode;

  @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<NumberGeneratorSequence> sequences = new ArrayList<>();

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
