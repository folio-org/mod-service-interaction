package org.olf

import grails.gorm.transactions.Transactional

import org.olf.ExternalUser

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.k_int.web.toolkit.refdata.RefdataValue


@Transactional
class DashboardService {
  // Create a dashboard for a resolved user
  Dashboard createDashboard(Map dashboardParams, ExternalUser user) {
    // Set up a dashboard with the parameters defined in POST, and Dashboard Access Object alongside it
    Dashboard dashboard = new Dashboard (dashboardParams).save(flush:true, failOnError: true);

    DashboardAccess dashboardAccess = new DashboardAccess([
      dashboard: dashboard,
      user: user,
      access: RefdataValue.lookupOrCreate('DashboardAccess.Access', 'manage')
    ]).save(flush:true, failOnError: true);

    return dashboard;
  }

  Dashboard createDefaultDashboard(ExternalUser user) {
    return createDashboard([name: "DEFAULT_${user.id}", widgets: []], user);
  }

  Integer countUserDashboards(ExternalUser user) {
    DashboardAccess.executeQuery("""SELECT COUNT(DISTINCT d.id) FROM DashboardAccess da INNER JOIN Dashboard d ON da.dashboard.id = d.id""")[0]
  }


  String accessLevel(String dashboardId, String userId) {
    return DashboardAccess.executeQuery("""
      SELECT access.value from DashboardAccess WHERE dashboard.id = :dashId AND user.id = :userId
    """.toString(), [dashId: dashboardId, userId: userId])
  }

  public boolean hasAccess(String desiredAccessLevel, String dashboardId, String userId) {
    String accessLevel = accessLevel(dashboardId, userId)
    switch (desiredAccessLevel) {
      case 'view':
        return accessLevel == 'view' || hasAccess('edit', dashboardId, userId)
      case 'edit':
        return accessLevel == 'edit' || hasAccess('manage', dashboardId, userId)
      case 'manage':
        return accessLevel == 'manage'
      default:
        log.error("Cannot declare access for unknown access level ${desiredAccessLevel}")
        return false;
    }
  }
}