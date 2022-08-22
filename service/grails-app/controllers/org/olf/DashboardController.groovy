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

  public String getDashboardId() {
    // This feels v flaky... the default REST endpoints use `id` and the others use dashboardId
    // May need better logic in future
    params.id ?: params.dashboardId
  }

  // TODO at some point in the future, we should look into replacing the explicit user checks
  // with granting internal spring permissions based on the access objects,
  // then querying for those permissions... Ask Steve. Should allow int-testing based on mocked perms
  public boolean hasAccess(String desiredAccessLevel) {
    String patronId = getPatron().id
    String dashboardId = getDashboardId()

    dashboardService.hasAccess(desiredAccessLevel, dashboardId, patronId)
  }

  public boolean hasAdminPerm() {
    return hasAuthority('okapi.servint.dashboards.admin')
  }

  public boolean matchesCurrentUser(String id) {
    return id == getPatron().id
  }

  public boolean canView() {
    return hasAccess('view') || hasAdminPerm()
  }

  public boolean canManage() {
    return hasAccess('manage') || hasAdminPerm()
  }

  public boolean canEdit() {
    return hasAccess('edit') || hasAdminPerm()
  }


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
    if (!canView()) {
      response.sendError(403)
    } else {
      respond doTheLookup(DashboardAccess) {
        eq 'dashboard.id', getDashboardId()
      }
    }
  }

  // Endpoint to edit the set of user access objects for a specific dashboard
  public def editDashboardUsers() {
    if (!canManage()) {
      response.sendError(403)
    } else {
      def data = getObjectToBind();
      String patronId = getPatron().id
      dashboardService.updateAccessToDashboard(getDashboardId(), data, patronId)
      getDashboardUsers()
    }
  }

  // Endpoint to edit dashboard access objects for a given user (For order weight, default etc)
  public def editUserDashboards() {
    def data = getObjectToBind();
    String patronId = getPatron().id

    for (def access : data) {
      if (!access.id) {
        response.sendError(400, "Can not edit access object with unspecified id. (${access})")
        return;
      }

      if (!access.user.id) {
        response.sendError(400, "Can not edit access object with unspecified user id. (${access})")
        return;
      }

      if (access.user.id != patronId) {
        response.sendError(403, "User (${patronId}) can not edit access object for another user. (${access})")
        return;
      }
    }

    log.debug("LOGDEBUG GOT HERE THOUGH :/")

    if (data.any { access -> access.user.id != patronId }) {
      response.sendError(403, "User (${patronId}) cannot edit user dashboards for another user")
    } else {
      dashboardService.updateUserDashboards(data, patronId)
      getUserSpecificDashboards()
    }
  }

  public def widgets() {
    if (!canView()) {
      response.sendError(403)
    } else {
      respond doTheLookup(WidgetInstance) {
        eq 'owner.id', getDashboardId()
      }
    }
  }

  def index(Integer max) {
    if (!hasAdminPerm()) {
      response.sendError(403)
    } else {
      super.index(max)
    }
  }

  def show() {
    if (!canView()) {
      response.sendError(403)
    } else {
      super.show()
    }
  }

  def delete() {
    if (!canManage()) {
      response.sendError(403)
    } else {
      // Ensure you delete all dashboard access objects before the dash itself
      dashboardService.deleteAccessObjects(getDashboardId())
      super.delete()
    }
  }

  def update() {
    if (!canEdit()) {
      response.sendError(403)
    } else {
      super.update()
    }
  }

  def save() {
    def data = getObjectToBind()
    String patronId = getPatron().id
    ExternalUser user = externalUserService.resolveUser(patronId);

    respond dashboardService.createDashboard(data, user);
  } 
}
