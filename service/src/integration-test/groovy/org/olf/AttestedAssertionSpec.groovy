package org.olf

import static org.springframework.http.HttpStatus.*

import com.k_int.okapi.OkapiHeaders
import com.k_int.okapi.OkapiTenantResolver
import geb.spock.GebSpec
import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import java.time.LocalDate
import spock.lang.Stepwise
import spock.lang.Unroll
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile
import groovy.util.logging.Slf4j
import org.olf.rfc8693.KeyPairService;
import org.olf.rfc8693.AttestedAssertionGeneratorService;
import org.olf.rfc8693.DBKeyPair;
import java.security.KeyPair;
import org.springframework.beans.factory.annotation.Autowired
import grails.gorm.multitenancy.Tenants

@Slf4j
@Integration
@Stepwise
class AttestedAssertionSpec extends BaseSpec {

	@Autowired
	KeyPairService keyPairService

	@Autowired
	AttestedAssertionGeneratorService attestedAssertionGeneratorService

  void "Key pair service test"() {
		KeyPair kp = null;
    when: 'We request a new private key'
			log.info("Get the current used for extSvc");
			withTenantNewTransaction {
				DBKeyPair.withTransaction { status ->
					kp = keyPairService.getCurrentKeyForUsage("extSvc");
				}
			}
    then: 'We got a key pair'
      log.debug("KP: ${kp}");
      kp != null;
  }

  void "AttestedAssertionGeneratorService service test"() {
		String key = null;
    when: 'We request an attestation jwt'
      withTenantNewTransaction {
        DBKeyPair.withTransaction { status ->
					key = attestedAssertionGeneratorService.generateAssertion('one','two','three');
        }
      }
    then: 'We got a key'
      log.debug("Key: ${key}");
      key != null;
  }

  void "Test the generation of our Attestation JWT"() {
		log.info("headers will be ${getAllHeaders()}");
		Map resp = null;
    when: 'We GET the attestation token for'
      resp = doGet("/servint/attestation/token", [for:'extSvc'])
    then: 'We got a response'
      log.debug("Got result ${resp}");
      resp != null;
  }

}

