package org.folio.servint.service.attestation;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legacy AttestedAssertionGeneratorService port — RFC 8693 attested
 * assertions: short-lived RS256 JWTs vouching for the calling user and
 * tenant, signed with the tenant's per-usage key pair. The kid header is
 * the signing key-pair record id (db_key_pair.kp_id): stable while that
 * key pair signs, different after any rotation, so receivers can pick the
 * exact matching public key (deviation D-21; the legacy kid was the
 * usage/audience string, which made rotation ambiguous — F-08).
 */
@Service
@RequiredArgsConstructor
public class AttestationService {

  private static final String ISSUER = "FOLIO::mod-service-interaction";
  private static final long VALIDITY_SECONDS = 300;

  private final KeyPairService keyPairService;

  @Transactional
  public String generateAssertion(String subject, String folioTenantId, String audience) {
    try {
      var signingKey = keyPairService.getCachedKeyForUsage(audience);

      var now = Instant.now();
      var claims = new JWTClaimsSet.Builder()
          .issuer(ISSUER)
          .subject(subject)
          .audience(audience)
          .issueTime(Date.from(now))
          .expirationTime(Date.from(now.plusSeconds(VALIDITY_SECONDS)))
          .jwtID(UUID.randomUUID().toString())
          .claim("tenant", folioTenantId)
          .build();

      var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
          .keyID(signingKey.kid())
          .type(JOSEObjectType.JWT)
          .build();

      var signedJwt = new SignedJWT(header, claims);
      signedJwt.sign(new RSASSASigner(signingKey.keyPair().getPrivate()));
      return signedJwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not sign attested assertion JWT", e);
    }
  }
}
