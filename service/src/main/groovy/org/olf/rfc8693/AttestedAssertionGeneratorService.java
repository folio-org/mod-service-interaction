package org.olf.rfc8693;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Service;
// Not until grails 7!
// import jakarta.annotation.Resource;
import javax.annotation.Resource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.olf.rfc8693.DBKeyPair;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import grails.gorm.multitenancy.CurrentTenant;
import grails.gorm.transactions.Transactional;

@Transactional
@CurrentTenant
@Service
public class AttestedAssertionGeneratorService {

  @Resource
  private KeyPairService keyPairService;

	public AttestedAssertionGeneratorService() {
	}

	@Transactional
	public String generateAssertion( String subject, String tenantId, String audience, String keyId) {

		String issuer = "issuer";
		String result = null;

		try {

			KeyPair keyPair = keyPairService.getCurrentKeyForUsage("extApp");

			Instant now = Instant.now();
			Instant exp = now.plusSeconds(300); // 5 minutes

			JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject(subject)
				.audience(audience)
				.issueTime(Date.from(now))
				.expirationTime(Date.from(exp))
				.jwtID(UUID.randomUUID().toString())
				.claim("tenant", tenantId)
				.build();

			JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(keyId) // lets the receiver pick the right public key
				.type(JOSEObjectType.JWT)
				.build();

			SignedJWT signedJWT = new SignedJWT(header, claims);

			JWSSigner signer = new RSASSASigner(keyPair.getPrivate());
			signedJWT.sign(signer);

			result = signedJWT.serialize(); // compact string: header.payload.signature
		} catch (JOSEException e) {
			throw new IllegalStateException("Could not sign attested assertion JWT", e);
		}
		
    return result;
	}
}

