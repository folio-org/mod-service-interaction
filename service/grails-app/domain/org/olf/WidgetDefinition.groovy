package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.WidgetType

class WidgetDefinition implements MultiTenant<WidgetDefinition> {

  String id
  String name
  String definition
  String definitionVersion
  String typeName
  String typeVersion

  static constraints = {
    name(unique: 'definitionVersion')
  }

  static mapping = {
                   id column: 'wdef_id', generator: 'uuid2', length:36
              version column: 'wdef_version'
    definitionVersion column: 'wdef_definition_version'
                 name column: 'wdef_name'
           definition column: 'wdef_definition'
             typeName column: 'wdef_type_name'
          typeVersion column: 'wdef_type_version'
  }

}
