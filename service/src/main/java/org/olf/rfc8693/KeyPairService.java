package org.olf.rfc8693.KeyPairService;

import org.springframework.stereotype.Service;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import java.util.Instant;
import java.time.temporal.ChronoUnit;
import org.olf.rfc8693.DBKeyPair;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


@Service
public class KeyPairService {


	@Resource
	private SessionFactory sessionFactory;

	@Transactional(readOnly = true)
	public KeyPair getCurrentKeyForUsage(String usage) {

		// We find the key with the lowest date created - there can be keys that will come available in the future but we don't immediately
		// use them so that downstream systems have a chance to assimilate the newer keys before we start using them.
		DBKeyPair kp = sessionFactory.getCurrentSession()
			.createQuery("from DBKeyPair kp where kp.usage = :usage and kp.dateCreated <= :now and kp.dateExpires >= :now order by kp.dateCreated asc", KeyPair.class)
      .setParameter("now", new Instant())
      .setParameter("uasge", usage)
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

		byte[] pubBytes = keyPair.getPublic().getEncoded();
		String pubString = Base64.getEncoder().encodeToString(pubBytes);

		byte[] privBytes = keyPair.getPrivate().getEncoded();
		String privString = Base64.getEncoder().encodeToString(privBytes);

		DBKeyPair result = new DBKeyPair();
		result.setDateCreated(new Instant().now());
		result.setDateExpires(new Instant().now().plus(730, ChronoUnit.DAYS));
		result.setUsage(usage);
		result.setAlg(kp.getAlgorthm());
		result.setPublicKey(pubString);
		result.setPrivateKey(privString);

		sessionFactory.currentSession().saveOrUpdate(result);
		sessionFactory.currentSession().flush();    

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
