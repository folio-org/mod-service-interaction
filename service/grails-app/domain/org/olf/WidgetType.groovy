package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

class WidgetType implements MultiTenant<WidgetType> {

  String id
  String name
  String typeVersion
  String schema

  static constraints = {
    name(unique: 'typeVersion')
  }

  static mapping = {
               id column: 'wtype_id', generator: 'uuid2', length:36
          version column: 'wtype_version'
      typeVersion column: 'wtype_type_version'
             name column: 'wtype_name'
           schema column: 'wtype_schema'
  }

}
