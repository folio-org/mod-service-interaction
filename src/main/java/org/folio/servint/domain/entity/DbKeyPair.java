package org.folio.servint.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Legacy DBKeyPair (table db_key_pair, cols kp_*) — per-tenant RSA key
 * pairs backing attestation signing, stored Base64-encoded (X.509 public,
 * PKCS#8 private).
 */
@Entity
@Table(name = "db_key_pair")
@Getter
@Setter
public class DbKeyPair {

  @Id
  @Column(name = "kp_id", length = 36)
  private String id;

  @Version
  @Column(name = "kp_version", nullable = false)
  private Long version;

  @Column(name = "kp_available_from")
  private Instant availableFrom;

  @Column(name = "kp_expires_at")
  private Instant expiresAt;

  @Column(name = "kp_usage", length = 32)
  private String usage;

  @Column(name = "kp_alg", length = 32)
  private String alg;

  @Column(name = "kp_public_key")
  private String publicKey;

  @Column(name = "kp_private_key")
  private String privateKey;

  @PrePersist
  void assignId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }
  }
}
