package org.olf

import grails.gorm.transactions.Transactional

import org.olf.UserProxy

class UserProxyService {
  UserProxy resolveUser(String uuid) {
    UserProxy resolvedUser = UserProxy.findByUserUuid(uuid) ?: new UserProxy(
                                                        userUuid: uuid,
                                                        dashboards: []
                                                      ).save(flush:true, failOnError: true);
    resolvedUser
  }
}