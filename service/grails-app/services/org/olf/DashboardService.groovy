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
  Dashboard createDashboard(Map dashboardParams, ExternalUser user, boolean defaultUserDashboard = false) {
    // Set up a dashboard with the parameters defined in POST, and Dashboard Access Object alongside it
    Dashboard dashboard = new Dashboard ([
      name: dashboardParams.name,
      widgets: dashboardParams.widgets ?: [],
    ]).save(flush:true, failOnError: true);

    Integer dashboardCount = countUserDashboards(user);
    DashboardAccess dashboardAccess = new DashboardAccess([
      dashboard: dashboard,
      user: user,
      access: RefdataValue.lookupOrCreate('DashboardAccess.Access', 'manage'),
      userDashboardWeight: dashboardCount,
      defaultUserDashboard: defaultUserDashboard
    ]).save(flush:true, failOnError: true);

    return dashboard;
  }

  // TODO eventually we should be always creating these from client and so this method will vanish
  Dashboard createDefaultDashboard(ExternalUser user) {
    return createDashboard([name: "My dashboard", widgets: []], user, true);
  }

  Integer countUserDashboards(ExternalUser user) {
    DashboardAccess.executeQuery("""SELECT COUNT(DISTINCT d.id) FROM DashboardAccess da INNER JOIN Dashboard d ON da.dashboard.id = d.id WHERE da.user.id = :userId""".toString(), [userId: user.id])[0]
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
   * A method to update a list of users on a dashboard, or more accurately to
   * bulk update a list of DashboardAccess objects which all point at a given dashboard,
   * to edit the access granted to certain users on said dashboard.
   *
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
   * and currentUserId must be the UUID pertaining to the currently logged in user.
   * The logic will ignore any requests to change or add the currently logged in user's access
   * Changes to user level characteristics like order weight and "default" dashboard will be ignored
   *
   * As with other methods in this service, permissions/access level must be checked by the calling code.
   */
  public void updateAccessToDashboard(String dashboardId, Collection<Map> userAccess, String currentUserId) {
    log.debug("DashboardService::updateAccessToDashboard called for (${dashboardId}) with access ${userAccess}")
    Dashboard dash = Dashboard.get(dashboardId);
    
    userAccess.each { access ->
      DashboardAccess.withNewTransaction {
        if (access.user.id == currentUserId) {
          log.warn("DashboardAccess can not currently be changed for the currently logged in user")
        } else if (!access.id) {
          // If we are not editing an existing access object then we can just create a new one

          // First check one doesn't already exist for this user (easiest is by checking access level
          if (hasAccess('view', dashboardId, access.user.id)) {
            // There is an existing DashboardAccess object for this user already -- need decision on what to do here
            // For now, log and ignore            
            log.warn("Ignoring DashboardAccess creation request since a DashboardAccess object already exists for this user (${access.user.id})")
          } else {
            // We have a genuinely new access object being requested.
            ExternalUser user = externalUserService.resolveUser(access.user.id);
            Integer dashboardCount = countUserDashboards(user);

            new DashboardAccess([
              user: user,
              dashboard: dash,
              access: access.access,
              userDashboardWeight: dashboardCount, // Hopefully append to end of dashboards list from a weight perspective
              defaultUserDashboard: (dashboardCount == 0) // Set to default if the first dashboard this user has
            ]).save(flush: true, failOnError: true)
          }
        } else {
          // We are attempting to edit an existing access object for this dashboard

          // Fetch the existing DashboardAccess object for comparison
          DashboardAccess existingAccess = DashboardAccess.get(access.id);
          if (access.dashboard.id != dashboardId) {
            log.warn("Dashboard access object (${access.id}) dashboard id mismatch. Expected ${dashboardId}, but got ${access.dashboard.id}. Ignoring any requested changes")
          } else if (access._delete) {
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
              dashboard: existingAccess.dashboard,
              userDashboardWeight: existingAccess.userDashboardWeight,
              defaultUserDashboard: existingAccess.defaultUserDashboard,
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


  /*
   * A method to update a list of user dashboards, or more accurately to
   * bulk update a list of DashboardAccess objects which all point at a given user,
   * to edit characteristics like order weight or "default" marked dashboard.
   *
   * Accepts userAccess block of the form
    [
      {
        id: <id of access object>
        user: {
          id: <user uuid>
        },
        dashboard: {
          id: <dashboard uuid>
        },
        userDashboardWeight: <Integer>,
        defaultUserDashboard: <boolean>
      },
      ...
    ]
   * and currentUserId must be the UUID pertaining to the currently logged in user.
   * This method will then set the user dashboard weight fields for each of the specified access objects
   * Any access objects NOT for the currentUserId will be ignored.
   *
   * Access level and user changes will be ignored
   */
  public void updateUserDashboards(Collection<Map> userAccess, String currentUserId) {
    log.debug("DashboardService::updateUserDashboards called for user (${currentUserId}) with access ${userAccess}")
    userAccess.each { access ->
      DashboardAccess.withNewTransaction {
        if (!access.id) {
          // Do not allow creation of access objects through this method
          log.warn("DashboardAccess can not be created through DashboardService::updateUserDashboards, ignoring.")
        } else if (access.user.id != currentUserId) {
          // This should probably be initially denied in the controller
          log.warn("DashboardAccess can not be changed for other users through DashboardService::updateUserDashboards, ignoring.")
        } else {
          // At this stage we have an existing dashboard access object for the currently logged in user
          // Fetch the access Object
          DashboardAccess existingAccess = DashboardAccess.get(access.id);

          Integer existingAccessWeight = existingAccess.userDashboardWeight;
          boolean existingDefault = existingAccess.defaultUserDashboard;

          // Change order weight if necessary
          existingAccess.properties = [
            id: existingAccess.id,
            access: existingAccess.access,
            user: existingAccess.user,
            dashboard: existingAccess.dashboard,
            userDashboardWeight: access.userDashboardWeight,
            defaultUserDashboard: access.defaultUserDashboard
          ]

          // If weight/default has not changed, do nothing to avoid database churn
          if (
            existingAccessWeight != existingAccess.userDashboardWeight ||
            existingDefault != existingAccess.defaultUserDashboard
          ) {
            existingAccess.save(flush: true, failOnError: true)
          }
        }
      }
    }
  }
}