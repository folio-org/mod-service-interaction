package org.olf

import grails.gorm.transactions.Transactional

import org.olf.ExternalUser

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import com.k_int.web.toolkit.refdata.RefdataValue


@Transactional
class DashboardService {
  ExternalUserService externalUserService

  // Create a dashboard for a resolved user
  Dashboard createDashboard(Map dashboardParams, ExternalUser user) {
    // Set up a dashboard with the parameters defined in POST, and Dashboard Access Object alongside it
    Dashboard dashboard = new Dashboard ([
      name: dashboardParams.name,
      widgets: dashboardParams.widgets ?: [],
    ]).save(flush:true, failOnError: true);

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
    """.toString(), [dashId: dashboardId, userId: userId])[0]
  }

  public boolean hasAccess(String desiredAccessLevel, String dashboardId, String userId) {
    String accessLevel = accessLevel(dashboardId, userId);

    // If there is no access level, we can exit out early
    if (!accessLevel) {
      return false
    }

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

  // This method WILL delete all access objects associated with a dashboard.
  // It is PARAMOUNT that the calling code checks the correct access level or
  // permission is in place before this is called.
  public void deleteAccessObjects(String dashboardId) {
    DashboardAccess.executeUpdate("""
      DELETE FROM DashboardAccess WHERE dashboard.id = :dashId
    """.toString(), [dashId: dashboardId])
  }


  /*
   * Accepts userAccess block of the form
    [
      {
        id: <id of access object, or null if new>
        _delete: <true/false/null, true to delete, or ignored if false/null>,
        user: {
          id: <user uuid>
        },
        access: <Refdata binding object, either .id or direct string value>
      }
    ]
   * As with other methods in this service, permissions/access level must be checked by the calling code.
   */
  public void updateAccessToDashboard(String dashboardId, Collection<Map> userAccess) {
    log.debug("DashboardService::updateAccessToDashboard called for (${dashboardId}) with access ${userAccess}")
    Dashboard dash = Dashboard.get(dashboardId);
    
    userAccess.each { access ->
      DashboardAccess.withNewTransaction {
        // If we are not editing an existing access object then we can just create a new one
        if (!access.id) {
          // First check one doesn't already exist for this user (easiest is by checking access level
          if (hasAccess('view', dashboardId, access.user.id)) {
            // There is an existing DashboardAccess object for this user already -- need decision on what to do here
            // For now, log and ignore            
            log.warn("Ignoring DashboardAccess creation request since a DashboardAccess object already exists for this user (${access.user.id})")
          } else {
            // We have a genuinely new access object being requested.
            new DashboardAccess([
              user: externalUserService.resolveUser(access.user.id),
              dashboard: dash,
              access: access.access
            ]).save(flush: true, failOnError: true)
          }
        } else {
          // We are attempting to edit an existing access object for this dashboard

          // Fetch the existing DashboardAccess object for comparison
          DashboardAccess existingAccess = DashboardAccess.get(access.id);

          if (access._delete) {
            // We need to remove this access
            existingAccess.delete()
          } else {
            // At this point, the only thing we can change is the access
            String existingAccessId = existingAccess.access.id;

            // Use .properties to allow databinding of refdataValues directly
            existingAccess.properties = [
              id: existingAccess.id,
              access: access.access,
              user: existingAccess.user,
              dashboard: existingAccess.dashboard
            ]

            // If access has not changed, do nothing to avoid database churn
            if (existingAccessId != existingAccess.access.id) {
              existingAccess.save(flush: true, failOnError: true)
            }
          }
        }
      }
    }
  }
}