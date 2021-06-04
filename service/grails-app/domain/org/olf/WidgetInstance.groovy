package org.olf

import grails.gorm.MultiTenant

import com.k_int.web.toolkit.refdata.Defaults
import com.k_int.web.toolkit.refdata.RefdataValue

import org.olf.WidgetType

class WidgetInstance implements MultiTenant<WidgetInstance> {

  String id
  String name
  Integer weight
  String definitionName
  String definitionVersion

  String configuration

  static belongsTo = [ owner: Dashboard ]

  static mapping = {
                 id column: 'wins_id', generator: 'uuid2', length:36
            version column: 'wins_version'
               name column: 'wins_name'
             weight column: 'wins_weight'
     definitionName column: 'wins_definition_name'
  definitionVersion column: 'wins_definition_version'
      configuration column: 'wins_configuration'
              owner column: 'wins_owner_fk'
  }

  static constraints = {
    weight (nullable: true)
  }

  def beforeValidate() {
    def maxWeight = WidgetInstance.executeQuery(
        """SELECT MAX(wi.weight) FROM WidgetInstance wi WHERE wi.owner.id = :ownerId"""
      , [ownerId: this.owner.id])[0]
    
    def matchingWeight = WidgetInstance.executeQuery(
        """SELECT MAX(wi.weight) FROM WidgetInstance wi WHERE wi.owner.id = :ownerId AND wi.weight = :weightInt"""
      , [ownerId: this.owner.id, weightInt: this.weight])[0]

    if (this.weight == null) {
      // If weight is undefined, set it to the highest weight on the owner + 1
      if (maxWeight != null) {
        this.weight = maxWeight + 1;
      } else {
        this.weight = 0;
      }
    }
  }
}
