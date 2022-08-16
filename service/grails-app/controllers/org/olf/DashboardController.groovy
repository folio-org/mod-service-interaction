package org.olf

import grails.rest.*
import grails.converters.*

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import com.k_int.okapi.OkapiTenantAwareController

import org.olf.ExternalUserService
import org.olf.DashboardService

@Slf4j
@CurrentTenant
class DashboardController extends OkapiTenantAwareController<DashboardController> {

  DashboardController() {
    super(Dashboard)
  }

  ExternalUserService externalUserService
  DashboardService dashboardService

  static responseFormats = ['json', 'xml']

  public def getUserSpecificDashboards() {
    String patronId = getPatron().id
    log.debug("DashboardController::getUserSpecificDashboards called for patron (${patronId}) ")
    ExternalUser user = externalUserService.resolveUser(patronId);

    // For now create default dashboard if user has no dashboards when trying to view all their dashboards.
    // TODO probably want to remove this, and have splash screen on frontend when no dashboards exist
    def count = dashboardService.countUserDashboards(user)
    if (count == 0) {
      dashboardService.createDefaultDashboard(user)
    }

    respond doTheLookup(DashboardAccess) {
      eq 'user.id', user.id
    }
  }

  public def getDashboardUsers() {
    respond doTheLookup(DashboardAccess) {
      eq 'dashboard.id', params.dashboardId
    }
  }

  public def editDashboardUsers() {
    def data = getObjectToBind();
    // FIXME ensure that the person making the call cannot change their own access

    respond dashboardService.updateAccessToDashboard(params.dashboardId, data)
  }

  public def widgets() {
    respond doTheLookup(WidgetInstance) {
      eq 'owner.id', params.dashboardId
    }
  }

  public boolean hasAccess(String desiredAccessLevel) {
    String patronId = getPatron().id
    dashboardService.hasAccess(desiredAccessLevel, params.id, patronId)
  }

  public boolean hasAdminPerm() {
    hasAuthority('okapi.servint.dashboards.admin')
  }

  public boolean matchesCurrentUser(String id) {
    return id == getPatron().id
  }

  public boolean canRead() {
    return hasAccess('view') || hasAdminPerm()
  }

  public boolean canDelete() {
    return hasAccess('manage') || hasAdminPerm()
  }

  public boolean canEdit() {
    return hasAccess('edit') || hasAdminPerm()
  }

  def index(Integer max) {
    if (!hasAdminPerm()) {
      response.sendError(403)
    }
    super.index(max)
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

    // Ensure you delete all dashboard access objects before the dash itself
    dashboardService.deleteAccessObjects(params.id)

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
    String patronId = getPatron().id
    ExternalUser user = externalUserService.resolveUser(patronId);

    respond dashboardService.createDashboard(data, user);

  /* 
    // Dashboard creation is going to be a bit different
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
    } */
  } 
}
