package org.olf

import grails.rest.*
import grails.converters.*

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import com.k_int.okapi.OkapiTenantAwareController

@Slf4j
@CurrentTenant
class WidgetInstanceController extends OkapiTenantAwareController<WidgetInstanceController> {
  WidgetInstanceController() {
    super(WidgetInstance)
  }
  def externalUserService

  public getUserSpecificWidgetInstances() {
    String patronId = getPatron().id
    log.debug("WidgetInstanceController::getUserSpecificWidgetInstances called for patron (${patronId}) ")
    ExternalUser proxiedUser = externalUserService.resolveUser(patronId)
    respond doTheLookup({
      createAlias ('owner', 'widget_owner')
        eq 'widget_owner.owner.id', proxiedUser.id
    })
  }

  public boolean isOwner() {
    def widgetDashboard = WidgetInstance.executeQuery("""
      SELECT owner.id from WidgetInstance as wi WHERE wi.id = :wiId
    """, [wiId: params.id])[0]

    def dashboardOwner = Dashboard.executeQuery("""
      SELECT owner.id from Dashboard as d WHERE d.id = :dId
    """,[dId: widgetDashboard])[0]

    // Bear in mind dash.owner.id is the id of a ExternalUser, which SHOULD always be the FOLIO ID
    return matchesCurrentUser(dashboardOwner)
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
    def dash = Dashboard.read(data.owner?.id)
    // Check owner details match current patron, or that user has authority
    if (!dash) {
      response.sendError(404, "Cannot create dashboard through the widgetInstance.") 
    } else if (!canPost(dash.owner?.id)) {
      response.sendError(403, "User does not have permission to POST a widgetInstance to dashboard (${data.owner?.id})")
    } else {
      // ONLY save if perms valid AND dashboard exists
      super.save()
    }
  }
}
