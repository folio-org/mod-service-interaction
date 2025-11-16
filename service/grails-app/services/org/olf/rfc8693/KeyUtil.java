package org.olf.rfc8693;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

public class KeyUtil {

	public static KeyPair generateRsaKeyPair() {
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048); // or 3072 if you prefer
			return kpg.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("RSA algorithm not available", e);
		}
	}
}
