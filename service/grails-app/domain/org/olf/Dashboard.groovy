package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.ExternalUser

class Dashboard implements MultiTenant<Dashboard> {

  String id
  String name
  static belongsTo = [ owner: ExternalUser ]
  static hasMany = [ widgets: WidgetInstance ]

  static mapping = {
        id column:'dshb_id', generator: 'uuid2', length:36
    version column: 'dshb_version'
      name column:'dshb_name'
      owner column: 'dshb_owner_fk'
    widgets cascade: 'all-delete-orphan'
  }

}
