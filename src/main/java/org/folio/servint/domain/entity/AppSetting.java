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
 * Legacy web-toolkit AppSetting (table app_setting, cols st_*) — a
 * tenant-scoped setting keyed by section + key; settingType is a plain
 * string, per web-toolkit-ce 10.6.4.
 */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
public class AppSetting {

  @Id
  @Column(name = "st_id", length = 36)
  private String id;

  @Version
  @Column(name = "st_version", nullable = false)
  private Long version;

  @Column(name = "st_section")
  private String section;

  @Column(name = "st_key")
  private String key;

  @Column(name = "st_setting_type")
  private String settingType;

  @Column(name = "st_vocab")
  private String vocab;

  @Column(name = "st_default_value")
  private String defValue;

  @Column(name = "st_value")
  private String value;

  @Column(name = "st_hidden")
  private Boolean hidden;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
