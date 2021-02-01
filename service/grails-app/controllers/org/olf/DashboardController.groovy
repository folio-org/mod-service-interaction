package org.olf

import grails.rest.*
import grails.converters.*

import org.olf.Dashboard

import com.k_int.okapi.OkapiTenantAwareController
import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import org.olf.rs.workflow.*;

import org.olf.UserProxyService

class DashboardController extends OkapiTenantAwareController<Dashboard> {
  def userProxyService
  
  static responseFormats = ['json', 'xml']
  
  DashboardController() {
    super(Dashboard)
  }

  Set<Dashboard> userDashboard(String user) {
    UserProxy user = userProxyService.resolveUser(uuid)
    
    user.dashboards
  }

}
