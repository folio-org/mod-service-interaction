package org.olf.rfc8693;

import org.springframework.stereotype.Service;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
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
public class KeyPairService {

	@Resource
	private SessionFactory sessionFactory;

	@Transactional(readOnly = true)
	public KeyPair getCurrentKeyForUsage(String usage) {

		// We find the key with the lowest date created - there can be keys that will come available in the future but we don't immediately
		// use them so that downstream systems have a chance to assimilate the newer keys before we start using them.
		DBKeyPair kp = sessionFactory.getCurrentSession()
			.createQuery("from DBKeyPair kp where kp.usage = :usage and kp.availableFrom <= :now and kp.expiresAt >= :now order by kp.availableFrom asc", DBKeyPair.class)
      .setParameter("now", Instant.now())
      .setParameter("usage", usage)
			.setMaxResults(1)
			.uniqueResult();

		// Even if we find a valid key we should check if it is expiring soon and create a new key to cover the future. Ideally
		// we would inform the sysadmin in some way as downstream systems will likely need to know (Unless we can find a way to jwks expose the public key)

		// For now - create a new key
		kp = createKeyPair(usage);

		return loadKeyPair(kp.getPublicKey(), kp.getPrivateKey(), kp.getAlg());
	}

	@Transactional
	public DBKeyPair createKeyPair(String usage) {

		KeyPair kp = KeyUtil.generateRsaKeyPair();

		byte[] pubBytes = kp.getPublic().getEncoded();
		String pubString = Base64.getEncoder().encodeToString(pubBytes);

		byte[] privBytes = kp.getPrivate().getEncoded();
		String privString = Base64.getEncoder().encodeToString(privBytes);

		DBKeyPair result = new DBKeyPair();
		result.setAvailableFrom(Instant.now());
		result.setExpiresAt(Instant.now().plus(730, ChronoUnit.DAYS));
		result.setUsage(usage);
		result.setAlg(kp.getPublic().getAlgorithm());
		result.setPublicKey(pubString);
		result.setPrivateKey(privString);

		sessionFactory.getCurrentSession().saveOrUpdate(result);
		sessionFactory.getCurrentSession().flush();    

		return result;
	}
	
	public static KeyPair loadKeyPair(String publicKeyB64,
	                                  String privateKeyB64,
	                                  String algorithm) {
		try {
			byte[] pubBytes  = Base64.getDecoder().decode(publicKeyB64);
			byte[] privBytes = Base64.getDecoder().decode(privateKeyB64);

			KeyFactory keyFactory = KeyFactory.getInstance(algorithm);

			X509EncodedKeySpec pubSpec       = new X509EncodedKeySpec(pubBytes);
			PKCS8EncodedKeySpec privateSpec  = new PKCS8EncodedKeySpec(privBytes);

			PublicKey publicKey   = keyFactory.generatePublic(pubSpec);
			PrivateKey privateKey = keyFactory.generatePrivate(privateSpec);

			return new KeyPair(publicKey, privateKey);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to reconstruct KeyPair", e);
		}
	}
}
