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

  public triggerTypeImportClean() {
    def result = [:]
    log.debug("AdminController::triggerTypeImportClean");
    utilityService.triggerTypeImport(true)

    result.status = 'OK'
    render result as JSON
  }

  public ensureDisplayData() {
    def result = [:]
    log.debug("AdminController::ensureDisplayData");
    utilityService.ensureDisplayData()

    result.status = 'OK'
    render result as JSON
  }
}

