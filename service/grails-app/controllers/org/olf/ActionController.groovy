package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import grails.converters.JSON

@Slf4j
@CurrentTenant
class AdminController {
  def utilityService

  public AdminController() {
  }

  public triggerTypeImport() {
    def result = [:]
    log.debug("AdminController::triggerTypeImport");
    utilityService.triggerTypeImport()

    result.status = 'OK'
    render result as JSON
  }
}

