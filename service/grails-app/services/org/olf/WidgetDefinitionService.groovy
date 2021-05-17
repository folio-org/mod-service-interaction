package org.olf

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

import com.k_int.okapi.OkapiClient

class WidgetDefinitionService {
  OkapiClient okapiClient;
  static Set dashboardImplementors = [];
  static ArrayList definitions = []

  boolean implementorsChanged() {
    boolean result = false
    Set implementors = okapiClient.withTenant().interfaceProviders('dashboard','^1.0') as Set
    
    if (dashboardImplementors.size() < 1) {
      dashboardImplementors = implementors
      // No cached implementors, return true
      result = true
    } else {
      // Have cached implementors, return true iff not equal to fetched list
      result = !dashboardImplementors.equals(implementors)
    }
    return result
  }

  // Return all the widgetDefinitions from implementing modules
  def fetchDefinitions() {

    if (implementorsChanged() || definitions.size() < 1) {
      // If the implementors list has changed or we have no cached definitions, fetch all and store in cache
      def fetchedDefintions = []
      okapiClient.getMultiInterface(
        'dashboard',
        '^1.0',
        '/dashboard/definitions',
        [:]
      ) { jsonReturn ->
          // Protect against single, non-list items being returned here.
          if (jsonReturn instanceof Collection) {
            fetchedDefintions += jsonReturn
          } else {
            fetchedDefintions += [jsonReturn]
          }
        }

      definitions = fetchedDefintions
    }
    
    return definitions
  }
}