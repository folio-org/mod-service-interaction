package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.WidgetType

class WidgetDefinition implements MultiTenant<WidgetDefinition> {

  String id
  String name
  String definition
  WidgetType type

  static mapping = {
            id column: 'wdef_id', generator: 'uuid2', length:36
       version column: 'wdef_version'
          name column: 'wdef_name'
    definition column: 'wdef_definition'
          type column: "wdef_type_fk"
  }

}
