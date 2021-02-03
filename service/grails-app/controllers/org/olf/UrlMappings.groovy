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
        "/$user" (controller: 'dashboard', action: 'getUserDashboards')
        "/$user/$name" (controller: 'dashboard', action: 'getNamedUserDashboard')
      }
    }

  }
}