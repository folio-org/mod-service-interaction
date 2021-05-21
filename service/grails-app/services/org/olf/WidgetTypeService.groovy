package org.olf
import org.olf.WidgetType

class WidgetTypeService {
  def utilityService


  WidgetType latestCompatibleType(String name, String version) {
    // Fetch list of Types by name, then parse that list for those that are valid
    List<WidgetType> types = WidgetType.findAll(
      "FROM WidgetType as type WHERE type.name = :name", [name: name]
    ).findAll { wt -> utilityService.compatibleVersion(wt.typeVersion, version) }
    
    def type = types.max { t -> 
      def matcher = utilityService.versionMatcher(t.typeVersion)
      if (matcher.matches()) {
        return matcher.group('MINOR') as Integer
      }
      return -1;
    }

    type
  }

  def typeListToString ( List types ) {
    types.collect { t -> "${t.name} (v${t.typeVersion})"}
  }

  
}