package org.olf

import java.util.regex.Matcher

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.grails.io.support.PathMatchingResourcePatternResolver
import org.grails.io.support.Resource

import org.everit.json.schema.Schema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import org.everit.json.schema.ValidationException

import org.grails.io.support.PathMatchingResourcePatternResolver
import org.grails.io.support.Resource
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

class UtilityService {
  def jsonSlurper = new JsonSlurper()
  PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver()

  def getJSONFileFromClassPath(classPath) {
    Resource jsonFile = resolver.getResource("classpath:${classPath}")
    def stream = jsonFile.getInputStream()
    return jsonSlurper.parse(stream)
  }

  def getJSONFilesFromClassPath(classPath) {
    Resource[] jsonFiles = resolver.getResources("classpath:${classPath}")
    return jsonFiles.collect {jf -> 
      def stream = jf.getInputStream()
      def parsedJson = jsonSlurper.parse(stream)
      return parsedJson
    }
  }

  public Matcher versionMatcher (String version) {
    return version =~ /(?<MAJOR>0|(?:[1-9]\d*))\.(?<MINOR>0|(?:[1-9]\d*))/
  }


  // Will return true if incomingVersion is compatible with comparisonVersion
  public boolean compatibleVersion (String incomingVersion, String comparisonVersion) {

    def incomingMatcher = versionMatcher(incomingVersion)
    def comparisonMatcher = versionMatcher(comparisonVersion)

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
        }
      }
    } else {
      log.warn("Semver version match error for ${incomingVersion} and/or ${comparisonVersion}")
    }

    return result;
  }

  public void validationExceptionLogger ( ValidationException e, Integer tabLevel = 0) {
    // First print the initial error
    log.error("${"\t" * tabLevel}${e.message}")
    if (e.causingExceptions.size() > 0) {
      // We have causing exceptions, go one step deeper
      e.causingExceptions.stream().each {ce ->
        validationExceptionLogger(ce, tabLevel + 1)
      }
    }
  }

  /* Utility function to validate incoming JSON object against a JSON schema */
  public boolean validateJsonAgainstSchema(def json, def schema) {
    boolean result = true;
    JSONObject rawSchema = new JSONObject(schema)

    log.info("UtilityService::validateAgainstSchema : Validating against schema (${rawSchema.title})")

    Schema loadedSchema = SchemaLoader.load(rawSchema)

    try {
      loadedSchema.validate(new JSONObject(json))
    } catch (ValidationException e) {
      validationExceptionLogger(e)
      result = false;
    }

    result;
  }

  public void triggerTypeImport(boolean cleanSlate = false) {
    log.info("UtilityService::TriggerTypeImport")
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver()
    def jsonSlurper = new JsonSlurper()

    log.info 'Importing widget types'

    Resource[] widgetTypes = resolver.getResources("classpath:sample_data/widgetTypes/*")

    if (cleanSlate) {
      log.info 'Deleting existing widget types'
      WidgetType.executeUpdate('delete from WidgetType')
    }

    widgetTypes.each { resource ->
      def stream = resource.getInputStream()
      def wt = jsonSlurper.parse(stream)

      WidgetType widgetType = WidgetType.findByNameAndTypeVersion(wt.name, wt.version) ?: new WidgetType(
        name: wt.name,
        typeVersion: wt.version,
        schema: JsonOutput.toJson(wt.schema)
      ).save(flush: true, failOnError: true) 
    }
  }

}