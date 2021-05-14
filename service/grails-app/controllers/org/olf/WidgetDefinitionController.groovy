package org.olf

import grails.rest.*
import grails.converters.*

import com.k_int.okapi.OkapiClient

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import com.k_int.okapi.OkapiTenantAwareController

@Slf4j
@CurrentTenant
class WidgetDefinitionController extends OkapiTenantAwareController<WidgetDefinitionController> {
  OkapiClient okapiClient;

  WidgetDefinitionController() {
    super(WidgetDefinition)
  }

  // Return all the widgetDefinitions from implementing modules
  def fetchDefinitions () {
    def multipleCall = []
    okapiClient.getMultiInterface(
      'dashboard',
      '^1.0',
      '/dashboard/definitions',
      [:]
    ) { jsonReturn ->
        // Protect against single, non-list items being returned here.
        if (jsonReturn instanceof Collection) {
          multipleCall += jsonReturn
        } else {
          multipleCall += [jsonReturn]
        }
      }

    respond multipleCall
  }
}
