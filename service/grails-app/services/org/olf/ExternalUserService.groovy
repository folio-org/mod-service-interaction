package org.olf

import grails.gorm.transactions.Transactional

import org.olf.ExternalUser

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

@Transactional
class ExternalUserService {
  ExternalUser resolveUser(String uuid) {
    ExternalUser resolvedUser = ExternalUser.read(uuid)
    if (!resolvedUser) {
      resolvedUser = new ExternalUser(
        dashboards: []
      )
      resolvedUser.id = uuid
      resolvedUser.save(flush:true, failOnError: true);
    }

    // Create default dashboard if none exist
    def userDashboards = Dashboard.executeQuery(
        """SELECT COUNT(dash.id) FROM Dashboard as dash WHERE dash.owner.id = :ownerId"""
      , [ownerId: resolvedUser.id])[0]
    if (userDashboards < 1) {
      // No existing dashboards, create one called DEFAULT_<id>
      new Dashboard (
        name: "DEFAULT_${resolvedUser.id}",
        owner: resolvedUser,
        widgets: [],
      ).save(flush:true, failOnError: true);
    }

    //Refetch user in case dashboard has been added
    resolvedUser = ExternalUser.read(resolvedUser.id)
    
    resolvedUser
  }
}