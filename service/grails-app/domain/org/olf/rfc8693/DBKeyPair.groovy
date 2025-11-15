package org.olf.rfc8693;

import grails.gorm.MultiTenant

class DBKeyPair implements MultiTenant<KeyPair> {

  String id
  Instant createdAt
  Instant expiresAt
  String usage
  String alg
  String publicKey
  String privateKey

  static constraints = {
  }

  static mapping = {
		table 'key_pair'
                   id column: 'kp_id', generator: 'uuid2', length:36
              version column: 'kp_version'
            createdAt column: 'kp_created_at'
            expiresAt column: 'kp_expires_at'
                usage column: 'kp_usage'
                  alg column: 'kp_alg'
            publicKey column: 'kp_public_key'
           privateKey column: 'kp_private_key'
  }

}

