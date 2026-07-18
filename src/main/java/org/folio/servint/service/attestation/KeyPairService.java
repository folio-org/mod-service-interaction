package org.folio.servint.service.attestation;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.servint.domain.entity.DbKeyPair;
import org.folio.servint.repository.DbKeyPairRepository;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy KeyPairService port: per-usage signing keys selected
 * earliest-valid-first (availableFrom &lt;= now &lt;= expiresAt, ordered by
 * availableFrom ascending) so future keys can be pre-published, created on
 * demand as RSA-2048 valid for 730 days, stored Base64-encoded (X.509
 * public, PKCS#8 private). The legacy in-memory cache keyed by usage alone
 * would leak keys across schemas in a multi-tenant deployment, so the port
 * keys the cache by (tenant, usage). Per review finding F-08 the cache
 * entry carries the DB row's identity and expiry: validity is re-checked
 * against {@code kp_expires_at} on every read (an expired entry reloads
 * from the DB, creating a fresh key on demand), and a tenant's entries are
 * evicted on tenant purge so a re-enabled tenant never reuses a key whose
 * row was dropped with the schema.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class KeyPairService {

  /**
   * A cached signing key with the metadata needed for lifecycle checks:
   * {@code kid} is the backing db_key_pair row id ({@code kp_id}) — stable
   * for the lifetime of the key pair, different after any rotation — and
   * {@code expiresAt} mirrors the row's {@code kp_expires_at}.
   */
  public record SigningKey(KeyPair keyPair, String kid, Instant expiresAt) {

    boolean isValidAt(Instant now) {
      return expiresAt != null && !now.isAfter(expiresAt);
    }
  }

  private final DbKeyPairRepository dbKeyPairRepository;
  private final FolioExecutionContext folioExecutionContext;

  private final Map<String, SigningKey> cache = new ConcurrentHashMap<>();

  @Transactional
  public SigningKey getCachedKeyForUsage(String usage) {
    var cacheKey = folioExecutionContext.getTenantId() + ":" + usage;
    var now = Instant.now();
    return cache.compute(cacheKey, (key, cached) ->
        cached != null && cached.isValidAt(now) ? cached : getCurrentKeyForUsage(usage, now));
  }

  /** Drops every cached key of the tenant (tenant purge — F-08). */
  public void evictTenant(String tenantId) {
    if (cache.keySet().removeIf(key -> key.startsWith(tenantId + ":"))) {
      log.info("Evicted cached signing keys for tenant {}", tenantId);
    }
  }

  private SigningKey getCurrentKeyForUsage(String usage, Instant now) {
    var stored = dbKeyPairRepository.findValidByUsage(usage, now).stream()
        .findFirst()
        .orElseGet(() -> createKeyPair(usage, now));
    return new SigningKey(
        loadKeyPair(stored.getPublicKey(), stored.getPrivateKey(), stored.getAlg()),
        stored.getId(), stored.getExpiresAt());
  }

  private DbKeyPair createKeyPair(String usage, Instant from) {
    log.info("Creating a new {} key pair for tenant {}", usage, folioExecutionContext.getTenantId());
    var generated = generateRsaKeyPair();

    var stored = new DbKeyPair();
    stored.setAvailableFrom(from);
    stored.setExpiresAt(from.plus(2 * 365L, ChronoUnit.DAYS));
    stored.setUsage(usage);
    stored.setAlg(generated.getPublic().getAlgorithm());
    stored.setPublicKey(Base64.getEncoder().encodeToString(generated.getPublic().getEncoded()));
    stored.setPrivateKey(Base64.getEncoder().encodeToString(generated.getPrivate().getEncoded()));
    return dbKeyPairRepository.saveAndFlush(stored);
  }

  private static KeyPair generateRsaKeyPair() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA algorithm not available", e);
    }
  }

  static KeyPair loadKeyPair(String publicKeyB64, String privateKeyB64, String algorithm) {
    try {
      var keyFactory = KeyFactory.getInstance(algorithm);
      var publicKey = keyFactory.generatePublic(
          new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)));
      var privateKey = keyFactory.generatePrivate(
          new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyB64)));
      return new KeyPair(publicKey, privateKey);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to reconstruct KeyPair", e);
    }
  }
}
