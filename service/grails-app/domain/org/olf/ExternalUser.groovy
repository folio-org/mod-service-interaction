package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.Dashboard

class ExternalUser implements MultiTenant<ExternalUser> {
  // This is serving only as a reference to a FOLIO User that we can hang user specific objects off.
  // We shouldn't really add any properties to it
  String id

  static mapping = {
            id column: 'eu_id', generator: 'assigned'
       version column: 'eu_version'
  }

}
