package org.olf.numberGenerator

import grails.gorm.multitenancy.CurrentTenant
import groovy.util.logging.Slf4j
import com.k_int.okapi.OkapiTenantAwareController
import org.olf.numgen.NumberGeneratorSequence

@Slf4j
@CurrentTenant
class NumberGeneratorSequenceController extends OkapiTenantAwareController<NumberGeneratorSequenceController> {
  NumberGeneratorSequenceController() {
    super(NumberGeneratorSequence)
  }  
}
