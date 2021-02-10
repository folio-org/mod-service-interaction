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

  public boolean isOwner() {
    def wi = WidgetInstance.read(params.id)
    def dash = Dashboard.read(wi.owner.id)

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
