package org.olf

import grails.gorm.multitenancy.CurrentTenant
import grails.converters.JSON
import org.olf.rfc8693.AttestedAssertionGeneratorService;
import org.olf.rfc8693.DBKeyPair;
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.springframework.beans.factory.annotation.Autowired
import grails.gorm.multitenancy.Tenants
import org.springframework.security.core.userdetails.UserDetails
import grails.plugin.springsecurity.SpringSecurityService
import org.springframework.security.core.context.SecurityContextHolder

/**
 * SecurityTokenService is a feature flag enabled function for mod-service-interaction 
 * intended to support RFC8693. It generates signed tokens (an Attested Assertion)
 * which are of no use for gaining access to folio endpoints BUT can serve proof of
 * folio citizenship in creating third party services linked to a FOLIO identity.
 * ONLY apps which are granted the public key from this app will be able to vaidate
 * the attestation. The keys generated here must be Pushed from a FOLIO context
 * they cannot be "read" or "Pulled" by remote third party apps.
 */
@Slf4j
@CurrentTenant
class AttestedAssertionController {

  AttestedAssertionGeneratorService attestedAssertionGeneratorService;
	SpringSecurityService springSecurityService

  public AttestedAssertionController() {
  }

  public token() {
    log.info("AttestedAssertionController::token");

		log.info("Sec ctx: ${SecurityContextHolder.getContext().getAuthentication()}");

    def result = [:]
		UserDetails ud = springSecurityService.principal;
		String folioTenantId = Tenants.currentId()
		String folioUsername = ud?.getUsername() ?: 'UNKNOWN';
		
		log.info("Generating attestation assertion....${folioTenantId} ${folioUsername} ${ud}");


		Tenants.withCurrent {
			DBKeyPair.withNewTransaction {
			
				log.info("Existing keys: ${DBKeyPair.list()}")
				log.info("Test cert flow");
				String attestation = attestedAssertionGeneratorService.generateAssertion(folioUsername,folioTenantId,"extApp");
				log.info("Attestation: ${attestation}");
				result.token=attestation;
				result.status = 'OK'
			}
		}

    render result as JSON
  }

}

