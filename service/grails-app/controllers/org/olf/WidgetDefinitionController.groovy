package org.olf

import grails.rest.*
import grails.converters.*

import org.olf.WidgetDefinitionService

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import com.k_int.okapi.OkapiTenantAwareController

@Slf4j
@CurrentTenant
class WidgetDefinitionController extends OkapiTenantAwareController<WidgetDefinitionController> {
  def widgetDefinitionService

  WidgetDefinitionController() {
    super(WidgetDefinition)
  }

  // Return all the widgetDefinitions from implementing modules
  def fetchDefinitions () {
    respond widgetDefinitionService.fetchDefinitions(params.name, params.nameLike, params.version)
  }
}
