package org.olf

class UrlMappings {

  static mappings = {
    "/"(controller: 'application', action:'index');

    "/servint/settings/appSettings" (resources: 'setting');

    // Call /servint/refdata to list all refdata categories
    '/servint/refdata'(resources: 'refdata') {
      collection {
        "/$domain/$property" (controller: 'refdata', action: 'lookup')
      }
    }

    // Call /servint/dashboard/userId to fetch all dashboards associated with a given user
    "/servint/dashboard" (resources: 'dashboard') {
      collection {
        "/my-dashboards" (controller: 'dashboard', action: 'getUserSpecificDashboards')
      }

      "/users" ( controller: 'dashboard') {
        action = [GET: 'getDashboardUsers', POST: 'editDashboardUsers']
      }

      "/widgets" (action: 'widgets', method: 'GET')
    }

    "/servint/widgets/definitions" (resources: 'widgetDefinition') {
      collection {
        "/global" (controller: 'widgetDefinition', action: 'fetchDefinitions')
      }
    }

    "/servint/widgets/instances" (resources: 'widgetInstance') {
      collection {
        "/my-widgets" (controller: 'widgetInstance', action: 'getUserSpecificWidgetInstances')
      }
    }

    "/servint/widgets/types" (resources: 'widgetType')

    "/servint/admin/$action"(controller:'admin')

    "/servint/numberGenerators" (resources:'numberGenerator') {
      collection {
        "/getNextNumber" ( controller:'numberGenerator', action: 'getNextNumber')
      }
    }

    "/dashboard/definitions" (resources: 'widgetDefinition', method: 'GET')
  }
}
