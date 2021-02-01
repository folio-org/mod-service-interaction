package org.olf

import grails.rest.*
import grails.converters.*

import org.olf.UserProxy

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import org.olf.UserProxyService

@Slf4j
@CurrentTenant
class UserProxyController {
  def userProxyService
  
  static responseFormats = ['json', 'xml']
  
  Set<Dashboard> resolveUser(String user) {
    log.debug("UserProxyController::resolveUser called with userId ${user}")
    respond userProxyService.resolveUser(user)
  }

}
