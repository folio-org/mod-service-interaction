package org.olf.rfc8693;

import grails.gorm.MultiTenant
import java.time.Instant

class DBKeyPair implements MultiTenant<DBKeyPair> {

  String id
  Instant availableFrom
  Instant expiresAt
  String usage
  String alg
  String publicKey
  String privateKey

  static constraints = {
  }

  static mapping = {
		table 'db_key_pair'
                   id column: 'kp_id', generator: 'uuid2', length:36
              version column: 'kp_version'
        availableFrom column: 'kp_available_from'
            expiresAt column: 'kp_expires_at'
                usage column: 'kp_usage'
                  alg column: 'kp_alg'
            publicKey column: 'kp_public_key'
           privateKey column: 'kp_private_key'
  }

}

