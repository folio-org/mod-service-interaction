package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.Dashboard

class UserProxy implements MultiTenant<UserProxy> {

  String id

  static hasMany = [
    dashboards: Dashboard
  ]

  static mapping = {
            id column: 'up_id', generator: 'assigned'
       version column: 'up_version'
    dashboards cascade: 'all-delete-orphan'
  }

}
