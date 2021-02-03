package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

class WidgetType implements MultiTenant<WidgetType> {

  String id
  String name
  String widgetVersion
  String schema

  static mapping = {
               id column: 'wtype_id', generator: 'uuid2', length:36
          version column: 'wtype_version'
    widgetVersion column: 'wtype_widget_version'
             name column: 'wtype_name'
           schema column: 'wtype_schema'
  }

}
