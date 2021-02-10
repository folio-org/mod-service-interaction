package org.olf

import grails.rest.*
import grails.converters.*

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j

import com.k_int.okapi.OkapiTenantAwareController

@Slf4j
@CurrentTenant
class WidgetDefinitionController extends OkapiTenantAwareController<WidgetDefinitionController> {
  WidgetDefinitionController() {
    super(WidgetDefinition)
  }
}
