package org.olf

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import grails.converters.JSON

@Slf4j
@CurrentTenant
class AdminController {
  def utilityService
  def widgetDefinitionService

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

  /*
   * ResetDefinitionCache and ResetImplementorCache are subtly different
   * but should have roughly the same effect, allowing the re-calculation
   * of available definitions while running the system.
   *
   * This does feel like a halfway house, not *properly* supporting runtime
   * addition of new definitions, but this is a use case we don't have yet.
   */
  public resetDefinitionCache() {
    def result = [:]
    log.debug("AdminController::resetDefinitionCache");
    widgetDefinitionService.resetDefinitionCache()

    result.status = 'OK'
    render result as JSON
  }

  public resetImplementorCache() {
    def result = [:]
    log.debug("AdminController::resetImplementorCache");
    widgetDefinitionService.resetImplementorCache()

    result.status = 'OK'
    render result as JSON
  }
}

