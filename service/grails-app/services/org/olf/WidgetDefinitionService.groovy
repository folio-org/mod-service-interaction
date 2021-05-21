package org.olf

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

import org.olf.WidgetType

import com.k_int.okapi.OkapiClient

class WidgetDefinitionService {
  OkapiClient okapiClient;
  def utilityService
  def widgetTypeService

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

  /*
   * This takes in a list of existing definitions and a list of incoming definitions,
   * then parses out any incoming definitions with names already on the definition list.
   * This is to avoid two modules both attempting to implement a WidgetDefinition with the same
   * name as another module's WidgetDefinition
   */
  def parseOutExistingDefinitionNames (List definitionList, List incomingDefinitions) {

    def returnList = []
    if (definitionList.size() == 0) {
      returnList = incomingDefinitions
    } else {
      incomingDefinitions.each {idef ->
        def defWithMatchingName = definitionList.find { d -> d.name == idef.name }
        if (!defWithMatchingName) {
          returnList << idef
        } else {
          log.warn ("WidgetDefinition with name: ${idef.name} is already implemented by another module, discarding")
        }
      }
    }

    return returnList
  }


  /*
   * This method should return false if a widgetDefinition does not have a valid Type,
   * or does not validate within the type schema, and true if it does.
   */
  def validateDefinition (def widgetDefinition) {
    log.info("WidgetDefinitionService::validateDefinition : ${widgetDefinition.name} v${widgetDefinition.version}")
    def valid = false
    def defType = widgetDefinition.type

    if (defType?.name && defType?.version) {
      def compatibleType = widgetTypeService.latestCompatibleType(defType.name, defType.version)
      if (compatibleType) {
        valid = utilityService.validateJsonAgainstSchema(widgetDefinition.definition, compatibleType.schema)
      }
    }
    
    valid
  }

  def defListToString ( List defs ) {
    defs.collect { d -> "${d.name} (v${d.version})"}
  }

  /*
   * This takes in a list of definitions and a definition,
   * then works out how to resolve that definition into the list.
   * If the same name/version exist, then discard.
   * If an earlier minor version exists, replace
   * If a later minor version exists, discard
   * Else add to list
   */
  def resolveDefinitions (List definitionList, List incomingDefinitions) {
    def uniquelyNamedDefinitions = parseOutExistingDefinitionNames(definitionList, incomingDefinitions)
    // At this point we have an incoming list of definitions with names unique to the module
    // The next step is to resolve the versions and validate against the type.
    incomingDefinitions.each {idef ->
      def compatibleDefs = definitionList.findAll {d -> d.name == idef.name && utilityService.compatibleVersion(d.version, idef.version)}
      
      // If we have compatible definitions already in the list, ie with minor version >= incoming version, discard
      if (compatibleDefs?.size() == 0) {
        // At this point we have a version ie 1.4 which none of the existing versions are compatible with.
        
        // This includes versions 2.3 AND 1.2. The former is irrelevant but we want to remove 1.2 as part of including 1.4
        definitionList.removeAll { d -> d.name == idef.name && utilityService.compatibleVersion(idef.version, d.version)}

        // Now we can add in our new version

        // Now we check if the definition is valid

        if (validateDefinition(idef)) {
          definitionList << idef
        } else {
          log.warn("WidgetDefinition ${idef.name} (v${idef.version}) is not valid")
        }
        
      }
    }
  }

  // Return all the widgetDefinitions from implementing modules
  def fetchDefinitions(String name = null, String nameLike = null, String version = null) {

    // Decide whether or not to refetch list
    if (implementorsChanged() || definitions.size() < 1) {
      // If the implementors list has changed or we have no cached definitions, fetch all and store in cache
      def fetchedDefinitions = []
      okapiClient.getMultiInterface(
        'dashboard',
        '^1.0',
        '/dashboard/definitions',
        [:]
      ) { jsonReturn ->
          // Protect against single, non-list items being returned here.
          if (jsonReturn instanceof Collection) {
            resolveDefinitions(fetchedDefinitions, jsonReturn)
          } else {
            resolveDefinitions(fetchedDefinitions, [jsonReturn])
          }
        }

      log.info("New cache of WidgetDefinitions: ${defListToString(fetchedDefinitions)}")
      definitions = fetchedDefinitions
    }

    // Now deal with non-static fetching of specific definitions
    def returnList = definitions

    returnList = definitions.findAll{defn ->
      // if no name parameter we don't want to filter the list on that, same for version
      (!name || defn.name.toLowerCase() == name.toLowerCase()) &&
      (!nameLike || defn.name =~ /(?i)$nameLike/) &&
      (!version || utilityService.compatibleVersion (defn.version, version))
    }
    
    return returnList
  }
}