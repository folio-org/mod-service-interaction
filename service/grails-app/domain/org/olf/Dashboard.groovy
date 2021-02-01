package org.olf.general

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.UserProxy

class Dashboard implements MultiTenant<Dashboard> {

  String id
  String name
  static belongsTo = [ owner: UserProxy ]

  static mapping = {
       id column:'dshb_id', generator: 'uuid2', length:36
     name column:'dshb_name'
    owner column: 'dshb_owner_FK'
  }

}
