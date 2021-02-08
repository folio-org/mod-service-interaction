package org.olf

import grails.rest.*
import grails.converters.*

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import com.k_int.okapi.OkapiTenantAwareController

import org.olf.UserProxyService

@Slf4j
@CurrentTenant
class DashboardController extends OkapiTenantAwareController<DashboardController> {
  DashboardController() {
    super(Dashboard)
  }

  def userProxyService

  static responseFormats = ['json', 'xml']

  public getUserSpecificDashboards() {
    String patronId = getPatron().id
    log.debug("UserProxyController::getUserSpecificDashboards called for patron (${patronId}) ")
    UserProxy proxiedUser = userProxyService.resolveUser(patronId)
    def result = proxiedUser.dashboards
    render result as JSON
  }

  public boolean isOwner() {
    def dash = Dashboard.read(params.id)
    log.debug("DASHBOARD OWNER ${dash.owner.id}")
    log.debug("PATRON ${getPatron().id}")
    return true;
  }

  public boolean canRead() {
    return isOwner()
  }

  def show() {
    if (!canRead()) {
      response.sendError(403)
    } 
    respond super.show()
  }
}
