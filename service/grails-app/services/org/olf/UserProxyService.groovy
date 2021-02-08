package org.olf

import grails.gorm.transactions.Transactional

import org.olf.UserProxy

class UserProxyService {
  UserProxy resolveUser(String uuid) {
    UserProxy resolvedUser = UserProxy.read(uuid)
    if (!resolvedUser) {
      resolvedUser = new UserProxy(
        dashboards: []
      )
      resolvedUser.id = uuid
      resolvedUser.save(failOnError: true);
    }

    // Create default dashboard if not exists
    Dashboard dash = Dashboard.findByOwnerAndName(resolvedUser, "DEFAULT") ?: new Dashboard (
      name: "DEFAULT",
      owner: resolvedUser
    ).save(flush:true, failOnError: true);

    //TODO remove the below, it just adds an example widget if none are there for testing purposes
    if (dash.widgets.size() == 0) {
      def widgetInstance = new WidgetInstance(
        name: "ERM example widget",
        owner: dash,
        configuration: """
        {
          "resultColumns": [
            {
              "column": "agreementName",
              "label": "Overwritten column label"
            },
            {
              "column": "startDate"
            }
          ],
          "filterColumns": [
            {
              "column": "vendor",
              "filterValue": "EBSCO"
            }
          ]
        }
        """
      ).save(flush: true, failOnError: true)
    }

    //Refetch user in case dashboard has been added
    resolvedUser = UserProxy.read(resolvedUser.id)
    
    resolvedUser
  }
}