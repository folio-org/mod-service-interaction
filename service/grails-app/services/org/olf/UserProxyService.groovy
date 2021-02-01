package org.olf

import grails.gorm.transactions.Transactional

import org.olf.UserProxy

class UserProxyService {
  UserProxy resolveUser(String uuid) {
    UserProxy resolvedUser = UserProxy.findByUuid(uuid) ?: new UserProxy(
                                                        userUUID: uuid
                                                        dashboards: []
                                                      ).save(flush:true, failOnError: true);
    resolvedUser
  }
}