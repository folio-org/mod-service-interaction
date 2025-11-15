package org.olf.rfc8693;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class AttestedAssertionGenerator {

	private final KeyPair keyPair;
	private final String keyId;
	private final String issuer;      // your system id
	private final String audience;    // token endpoint / STS

	public AttestedAssertionGenerator(KeyPair keyPair,
	                                  String keyId,
	                                  String issuer,
	                                  String audience) {
		this.keyPair = keyPair;
		this.keyId = keyId;
		this.issuer = issuer;
		this.audience = audience;
	}

	public String generateAssertion(String subject, String tenantId) {
		try {
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

			return signedJWT.serialize(); // compact string: header.payload.signature
		} catch (JOSEException e) {
			throw new IllegalStateException("Could not sign attested assertion JWT", e);
		}
	}
}

