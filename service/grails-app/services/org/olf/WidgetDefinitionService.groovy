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
  def fetchDefinitions(String name = null, String nameLike = null, String version = null) {

    // Decide whether or not to refetch list
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

    // Now deal with non-static fetching of specific definitions
    def returnList = definitions
    returnList = definitions.findAll{defn ->
      // if no name parameter we don't want to filter the list on that, same for version
      (!name || defn.name.toLowerCase() == name.toLowerCase()) &&
      (!nameLike || defn.name =~ /(?i)$nameLike/) &&
      (!version || defn.version == version)
    }
    
    return returnList
  }
}