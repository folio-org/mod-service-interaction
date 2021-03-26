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

    // Create default dashboard if not exists
    Dashboard dash = Dashboard.findByOwnerAndName(resolvedUser, "DEFAULT") ?: new Dashboard (
      name: "DEFAULT",
      owner: resolvedUser,
      widgets: [],
    ).save(flush:true, failOnError: true);

    //Refetch user in case dashboard has been added
    resolvedUser = ExternalUser.read(resolvedUser.id)
    
    resolvedUser
  }
}