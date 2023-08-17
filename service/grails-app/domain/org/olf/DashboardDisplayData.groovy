package org.olf

import grails.gorm.MultiTenant

/* A domain class to hold all of the information about  */

class DashboardDisplayData implements MultiTenant<DashboardDisplayData> {

  String id
  String dashId
  String layoutData

  static mapping = {
            id column:'ddd_id', generator: 'uuid2', length:36
       version column: 'ddd_version'
        dashId column: 'ddd_dash_id'
    layoutData column: 'ddd_layout_data'
  }

  static constraints = {
         dashId (nullable:false, blank:false)
    layoutData (nullable: true, blank: false)
  }
}
