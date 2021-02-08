package org.olf

import grails.gorm.transactions.Transactional

import org.olf.ExternalUser

class ExternalUserService {
  ExternalUser resolveUser(String uuid) {
    ExternalUser resolvedUser = ExternalUser.read(uuid)
    if (!resolvedUser) {
      resolvedUser = new ExternalUser(
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
    WidgetDefinition definition = WidgetDefinition.findByNameAndDefinitionVersion("ERM Agreements", "v1.0.0")
    if (dash.widgets?.size() == 0) {
      def widgetInstance = new WidgetInstance(
        name: "ERM example widget",
        owner: dash,
        definition: definition,
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
    resolvedUser = ExternalUser.read(resolvedUser.id)
    
    resolvedUser
  }
}