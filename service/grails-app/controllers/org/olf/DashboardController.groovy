package org.olf

import grails.rest.*
import grails.converters.*

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import com.k_int.okapi.OkapiTenantAwareController

import org.olf.ExternalUserService

@Slf4j
@CurrentTenant
class DashboardController extends OkapiTenantAwareController<DashboardController> {
  DashboardController() {
    super(Dashboard)
  }

  def externalUserService
  static responseFormats = ['json', 'xml']

  public getUserSpecificDashboards() {
    String patronId = getPatron().id
    log.debug("DashboardController::getUserSpecificDashboards called for patron (${patronId}) ")
    ExternalUser proxiedUser = externalUserService.resolveUser(patronId)
    respond doTheLookup({
        eq 'owner.id', proxiedUser.id
    })
  }

  public boolean isOwner() {
    def dash = Dashboard.read(params.id)
    // Bear in mind dash.owner.id is the id of a ExternalUser, which SHOULD always be the FOLIO ID
    return matchesCurrentUser(dash.owner.id)
  }

  public boolean matchesCurrentUser(String id) {
    return id == getPatron().id
  }

  public boolean canRead() {
    return isOwner() || hasAuthority('okapi.servint.dashboards.admin')
  }

  public boolean canDelete() {
    return isOwner() || hasAuthority('okapi.servint.dashboards.admin')
  }

  public boolean canEdit() {
    return isOwner() || hasAuthority('okapi.servint.dashboards.admin')
  }

  public boolean canPost(String ownerId) {
    matchesCurrentUser(ownerId) || hasAuthority('okapi.servint.dashboards.admin')
  }

  def show() {
    if (!canRead()) {
      response.sendError(403)
    } 
    super.show()
  }

  def delete() {
    if (!canDelete()) {
      response.sendError(403)
    } 
    super.delete()
  }

  def update() {
    if (!canEdit()) {
      response.sendError(403)
    }
    super.update()
  }

  def save() {
    def data = getObjectToBind()
    // Check owner details match current patron, or that user has authority
    if (!canPost(data.owner?.id)) {
      response.sendError(403, "User does not have permission to POST a dashboard with owner (${data.owner?.id})")
    } else {
      def userExists = ExternalUser.read(data.owner?.id)
      if (!userExists) {
        response.sendError(404, "Cannot create user proxy through the dashboard. User must log in and access the endpoint '/servint/dashboards/my-dashboard' before dashboards can be assigned to them.")
      }
      // ONLY save if perms valid AND user exists
      super.save()
    }
  }
}
