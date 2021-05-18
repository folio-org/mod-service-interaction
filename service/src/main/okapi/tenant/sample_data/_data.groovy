import grails.gorm.multitenancy.Tenants
import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional
import com.k_int.web.toolkit.refdata.RefdataValue
import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.custprops.types.CustomPropertyRefdataDefinition
import com.k_int.web.toolkit.custprops.types.CustomPropertyText;
import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import grails.databinding.SimpleMapDataBindingSource
import static grails.async.Promises.*
import com.k_int.web.toolkit.settings.AppSetting

import org.grails.io.support.PathMatchingResourcePatternResolver
import org.grails.io.support.Resource
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import static groovy.io.FileType.FILES

import org.olf.WidgetType
import org.olf.WidgetDefinition

CustomPropertyDefinition ensureRefdataProperty(String name, boolean local, String category, String label = null) {

  CustomPropertyDefinition result = null;
  def rdc = RefdataCategory.findByDesc(category);

  if ( rdc != null ) {
    result = CustomPropertyDefinition.findByName(name)
    if ( result == null ) {
      result = new CustomPropertyRefdataDefinition(
                                        name:name,
                                        defaultInternal: local,
                                        label:label,
                                        category: rdc)
      // Not entirely sure why type can't be set in the above, but other bootstrap scripts do this
      // the same way, so copying. Type doesn't work when set as a part of the definition above
      result.type=com.k_int.web.toolkit.custprops.types.CustomPropertyRefdata.class
      result.save(flush:true, failOnError:true);
    }
  }
  else {
    println("Unable to find category ${category}");
  }
  return result;
}


// When adding new section names into this file please make sure they are in camel case.
CustomPropertyDefinition ensureTextProperty(String name, boolean local = true, String label = null) {
  CustomPropertyDefinition result = CustomPropertyDefinition.findByName(name) ?: new CustomPropertyDefinition(
                                        name:name,
                                        type:com.k_int.web.toolkit.custprops.types.CustomPropertyText.class,
                                        defaultInternal: local,
                                        label:label
                                      ).save(flush:true, failOnError:true);
  return result;
}


// TODO remove all of this when bootstrapping is no longer required
PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver()

def jsonSlurper = new JsonSlurper()

log.info 'Importing widget types'

Resource[] widgetTypes = resolver.getResources("classpath:sample_data/widgetTypes/*")

widgetTypes.each { resource ->
  def stream = resource.getInputStream()
  def wt = jsonSlurper.parse(stream)

  WidgetType widgetType = WidgetType.findByNameAndTypeVersion(wt.name, wt.version) ?: new WidgetType(
    name: wt.name,
    typeVersion: wt.version,
    schema: JsonOutput.toJson(wt.schema)
  ).save(flush: true, failOnError: true) 
}

log.info 'Importing widget definitions'


Resource[] widgetDefs = resolver.getResources("classpath:sample_data/widgetDefinitions/*")
widgetDefs.each { resource ->
  def stream = resource.getInputStream()
  def wd = jsonSlurper.parse(stream)

  WidgetType type = WidgetType.findByNameAndTypeVersion(wd.type.name, wd.type.version)
  if (type != null) {
    WidgetDefinition widgetDef = WidgetDefinition.findByNameAndDefinitionVersion(wd.name, wd.version) ?: new WidgetDefinition (
      name: wd.name,
      definitionVersion: wd.version,
      typeName: type.name,
      typeVersion: type.typeVersion,
      definition: JsonOutput.toJson(wd.definition)
    ).save(flush: true, failOnError: true)
  } else {
    log.warn "WidgetType ${wd.type.name} ${wd.type.version} is not supported"
  }
}

// TODO eventually we should not be bootstrapping these, but instead each app which wants to use the dashboard
// should be sending their definitions to an endpoint in mod-service-interaction.


log.info 'Importing sample data'

AppSetting test_app_setting = AppSetting.findByKey('test_app_setting') ?: new AppSetting(
                                  section:'test',
                                  settingType:'String',
                                  key: 'test_app_setting',
                                  ).save(flush:true, failOnError: true);


def cp_test = ensureTextProperty('test', false);
println("\n\n***Completed tenant setup***");
