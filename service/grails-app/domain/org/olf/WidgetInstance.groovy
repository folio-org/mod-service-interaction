package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.WidgetType

class WidgetInstance implements MultiTenant<WidgetInstance> {

  String id
  String name
  WidgetDefinition definition

  String configuration

  static belongsTo = [ owner: Dashboard ]

  static mapping = {
               id column: 'wins_id', generator: 'uuid2', length:36
          version column: 'wins_version'
             name column: 'wins_name'
       definition column: 'wins_definition_fk'
    configuration column: 'wins_configuration'
            owner column: 'wins_owner_fk'
  }

}
