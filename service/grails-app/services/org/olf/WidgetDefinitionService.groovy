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

  // Will return true if incomingVersion is compatible with comparisonVersion
  def compatibleVersion (String incomingVersion, String comparisonVersion) {

    def incomingMatcher = incomingVersion =~ /(?<MAJOR>0|(?:[1-9]\d*))\.(?<MINOR>0|(?:[1-9]\d*))/
    def comparisonMatcher = comparisonVersion =~ /(?<MAJOR>0|(?:[1-9]\d*))\.(?<MINOR>0|(?:[1-9]\d*))/


    def result = false;
    if (incomingMatcher.matches() && comparisonMatcher.matches()) {
      // If both matches succeed we have valid versioning. Else return false
      def incomingMajor = incomingMatcher.group('MAJOR')
      def comparisonMajor = comparisonMatcher.group('MAJOR')

      if (incomingMajor == comparisonMajor) {
        // If majors are equal, continue, else we can discard this as being compatible

        // Should be able to parse these to ints because the regex has already matched them as digits
        def incomingMinor = incomingMatcher.group('MINOR') as Integer
        def comparisonMinor = comparisonMatcher.group('MINOR') as Integer

        if (incomingMinor >= comparisonMinor) {
          result = true;
        } else {
          log.warn("Version ${incomingVersion} NON COMPATIBLE WITH ${comparisonVersion}")
        }
      } else {
        log.warn("Version ${incomingVersion} NON COMPATIBLE WITH ${comparisonVersion}")
      }
    } else {
      log.warn("Semver version match error for ${incomingVersion} and/or ${comparisonVersion}")
    }

    return result;
 
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
      (!version || compatibleVersion (defn.version, version))
    }
    
    return returnList
  }
}