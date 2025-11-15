package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import grails.converters.JSON


/**
 * SecurityTokenService is a feature flag enabled function for mod-service-interaction 
 * intended to support RFC8693. It generates signed tokens (an Attested Assertion)
 * which are of no use for gaining access to folio endpoints BUT can serve proof of
 * folio citizenship in creating third party services and assertions.
 */
@Slf4j
@CurrentTenant
class AttestedAssertionController {

  public AttestedAssertionController() {
  }

  public token() {
    def result = [:]
    log.debug("AttestedAssertionController::token");
    result.status = 'OK'
    render result as JSON
  }

}

