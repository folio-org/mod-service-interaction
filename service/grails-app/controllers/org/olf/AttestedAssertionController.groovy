package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import grails.converters.JSON
import org.olf.rfc8693.AttestedAssertionGenerator;



/**
 * SecurityTokenService is a feature flag enabled function for mod-service-interaction 
 * intended to support RFC8693. It generates signed tokens (an Attested Assertion)
 * which are of no use for gaining access to folio endpoints BUT can serve proof of
 * folio citizenship in creating third party services and assertions.
 */
@Slf4j
@CurrentTenant
class AttestedAssertionController {

  AttestedAssertionGenerator attestedAssertionGenerator;

  public AttestedAssertionController() {
  }

  public token() {
    log.debug("AttestedAssertionController::token");
		String attestation = attestedAssertionGenerator.generateAssertion("subject","tenantId","audience","keyId")

		log.info("Attestation: ${attestation}");

    def result = [:]
		result.token=attestation;
    result.status = 'OK'
    render result as JSON
  }

}

