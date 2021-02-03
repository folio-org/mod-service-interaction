package org.olf

import grails.rest.*
import grails.converters.*

import org.olf.UserProxy

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import org.olf.UserProxyService

@Slf4j
@CurrentTenant
class DashboardController {
  def userProxyService
  
  static responseFormats = ['json', 'xml']
  
  Set<Dashboard> getUserDashboards(String user) {
    log.debug("UserProxyController::getUserDashboards called with userId ${user}")
    UserProxy proxiedUser = userProxyService.resolveUser(user)
    respond proxiedUser.dashboards
  }

  Dashboard getNamedUserDashboard(String user, String name) {
    log.debug("UserProxyController::getNamedUserDashboard called with userId (${user}) and name (${name})")
    UserProxy proxiedUser = userProxyService.resolveUser(user)
    Dashboard dash = Dashboard.findByOwnerAndName(proxiedUser, name) ?: new Dashboard (
      name: name,
      owner: proxiedUser
    )

    respond dash
  }

}
