package org.folio.servint.service.attestation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.folio.servint.service.attestation.KeyPairService.SigningKey;
import org.junit.jupiter.api.Test;

/**
 * Validity-window check for the cached {@link SigningKey}: a key is usable up
 * to and including its expiry instant, and a null expiry is never valid (a
 * malformed cache entry must reload rather than sign, F-08).
 */
class SigningKeyValidityTest {

  private static final Instant EXPIRY = Instant.parse("2030-01-01T00:00:00Z");

  private SigningKey keyExpiringAt(Instant expiresAt) {
    return new SigningKey(null, "kid-1", expiresAt);
  }

  @Test
  void validBeforeExpiry() {
    assertTrue(keyExpiringAt(EXPIRY).isValidAt(EXPIRY.minusSeconds(1)));
  }

  @Test
  void validAtExactExpiryInstant() {
    // The window is inclusive of the expiry instant (not now.isAfter(expiresAt)).
    assertTrue(keyExpiringAt(EXPIRY).isValidAt(EXPIRY));
  }

  @Test
  void invalidAfterExpiry() {
    assertFalse(keyExpiringAt(EXPIRY).isValidAt(EXPIRY.plusSeconds(1)));
  }

  @Test
  void invalidWhenExpiryIsNull() {
    assertFalse(keyExpiringAt(null).isValidAt(EXPIRY));
  }
}
