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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Legacy org.olf.numgen.NumberGeneratorSequence (table
 * number_generator_sequence, cols ngs_*). prefix/postfix are deprecated but
 * preserved — the default output template still renders them.
 */
@Entity
@Table(name = "number_generator_sequence")
@Getter
@Setter
public class NumberGeneratorSequence {

  @Id
  @Column(name = "ngs_id", length = 36)
  private String id;

  @Version
  @Column(name = "ngs_version", nullable = false)
  private Long version;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ngs_owner", nullable = false)
  private NumberGenerator owner;

  @Column(name = "ngs_code", nullable = false, length = 36)
  private String code;

  @Column(name = "ngs_name", length = 100)
  private String name;

  @Column(name = "ngs_prefix", length = 100)
  private String prefix;

  @Column(name = "ngs_postfix", length = 100)
  private String postfix;

  @Column(name = "ngs_format", length = 20)
  private String format;

  @Column(name = "ngs_next_value")
  private Long nextValue;

  @Column(name = "ngs_pre_checksum_template", length = 256)
  private String preChecksumTemplate;

  @Column(name = "ngs_output_template", length = 256)
  private String outputTemplate;

  @Column(name = "ngs_description", length = 256)
  private String description;

  @Column(name = "ngs_enabled")
  private Boolean enabled = Boolean.TRUE;

  @Column(name = "ngs_reset_on_year_change")
  private Boolean resetOnYearChange = Boolean.FALSE;

  @Column(name = "ngs_last_used_year", length = 4)
  private String lastUsedYear;

  @Column(name = "ngs_maximum_number")
  private Long maximumNumber;

  @Column(name = "ngs_maximum_number_threshold")
  private Long maximumNumberThreshold;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ngs_check_digit_algorithm_fk")
  private RefdataValue checkDigitAlgo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ngs_maximum_check_fk")
  private RefdataValue maximumCheck;

  public static String currentYear() {
    return String.valueOf(LocalDate.now(ZoneOffset.UTC).getYear());
  }

  /** lastUsedYear != null: only a sequence that has actually been used resets. */
  public boolean isYearResetPending(String year) {
    return Boolean.TRUE.equals(resetOnYearChange) && lastUsedYear != null && !lastUsedYear.equals(year);
  }

  public boolean isYearResetPending() {
    return isYearResetPending(currentYear());
  }

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
