package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.CategoryId
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.ExternalUser
import org.olf.Dashboard

class DashboardAccess implements MultiTenant<DashboardAccess> {

  String id
  Dashboard dashboard
  ExternalUser user

  // Allow sorting of a particular user's dashboards by weight.
  Integer userDashboardWeight

  // Set specified dashboard to be the default dahsboard for the specified user
  boolean defaultUserDashboard = false

  Date dateCreated

  @CategoryId(defaultInternal=true)
  @Defaults(['Manage', 'Edit', 'View'])
  RefdataValue access

  static mapping = {
                      id column:'da_id', generator: 'uuid2', length:36
                 version column: 'da_version'
               dashboard column: 'da_dashboard_fk'
                    user column: 'da_user_fk'
                  access column: 'da_access_fk'
             dateCreated column: 'da_date_created'
     userDashboardWeight column: 'da_user_dashboard_weight'
    defaultUserDashboard column: 'da_default_user_dashboard'
  }
}
