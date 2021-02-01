package org.olf.general

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.Dashboard

class UserProxy implements MultiTenant<UserProxy> {

  String id
  String userUUID

  static hasMany = [
    dashboards: Dashboard
  ]

  static mapping = {
            id column: 'up_id', generator: 'uuid2', length:36
      userUUID column: 'up_user_uuid'
    dashboards cascade: 'all-delete-orphan'
  }

}
