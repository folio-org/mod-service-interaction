package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.ExternalUser

class Dashboard implements MultiTenant<Dashboard> {

  String id
  String name
  String description

  /* Due to the nature of dashboard, we need some display only data
   * To avoid having to model this and locking ourselves to a particular
   * frontend structure, store as RAW json we can send/parse in the frontend
   */
  String displayData

  static hasMany = [ widgets: WidgetInstance ]

  static mapping = {
             id column:'dshb_id', generator: 'uuid2', length:36
        version column: 'dshb_version'
           name column:'dshb_name'
    description column: 'dshb_description'
    displayData column: 'dshb_display_data'
        widgets cascade: 'all-delete-orphan'
  }

  static constraints = {
           name (nullable:true, blank:false)
    description (nullable:true, blank:false)
    displayData (nullable: true, blank: false)
  }
}
