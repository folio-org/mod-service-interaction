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
class WidgetInstanceController extends OkapiTenantAwareController<WidgetInstanceController> {
  WidgetInstanceController() {
    super(WidgetInstance)
  }

  ExternalUserService externalUserService
  DashboardService dashboardService

// FIXME I don't think this should exist
/*   public getUserSpecificWidgetInstances() {
    String patronId = getPatron().id
    log.debug("WidgetInstanceController::getUserSpecificWidgetInstances called for patron (${patronId}) ")
    ExternalUser proxiedUser = externalUserService.resolveUser(patronId)
    respond doTheLookup({
      createAlias ('owner', 'widget_owner')
        eq 'widget_owner.owner.id', proxiedUser.id
    })
  } */

  // Direct access check with explicit dashId
  public boolean hasAccessToDashboard(String desiredAccessLevel, String dashId) {
    String patronId = getPatron().id;
    return dashboardService.hasAccess(desiredAccessLevel, dashId, patronId)
  }

  // Check access on dashboard which is owner of widget specified by params.id
  public boolean hasAccess(String desiredAccessLevel) {
    WidgetInstance widget = WidgetInstance.read(params.id);
    return hasAccessToDashboard(desiredAccessLevel, widget.owner.id)
  }

  public boolean hasAdminPerm() {
    hasAuthority('okapi.servint.dashboards.admin.allops')
  }

  public boolean matchesCurrentUser(String id) {
    return id == getPatron().id
  }

  public boolean canRead() {
    return hasAccess('view') || hasAdminPerm()
  }

  public boolean canDelete() {
    return hasAccess('edit') || hasAdminPerm()
  }

  public boolean canEdit() {
    return hasAccess('edit') || hasAdminPerm()
  }

  public boolean canPost(String dashId) {
    hasAccessToDashboard('edit', dashId) || hasAdminPerm()
  }

  def index(Integer max) {
    if (!hasAdminPerm()) {
      response.sendError(403)
    } else {
      super.index(max)
    }
  }

  def show() {
    if (!canRead()) {
      response.sendError(403)
    } else {
      super.show()
    }
  }

  def delete() {
    if (!canDelete()) {
      response.sendError(403)
    } else {
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
    def dash = Dashboard.read(data.owner?.id)

    // Check owner details match current patron, or that user has authority
    if (!dash) {
      response.sendError(404, "Cannot create dashboard through the widgetInstance.") 
    } else if (!canPost(data.owner?.id)) {
      response.sendError(403, "User does not have permission to POST a widgetInstance to dashboard (${data.owner?.id})")
    } else {
      // ONLY save if perms valid AND dashboard exists
      super.save()
    }
  }
}
