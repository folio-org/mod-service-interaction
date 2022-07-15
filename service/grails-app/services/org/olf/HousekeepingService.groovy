package org.olf

import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.custprops.CustomPropertyDefinition
import com.k_int.web.toolkit.refdata.RefdataCategory
import com.k_int.web.toolkit.refdata.RefdataValue;
import com.k_int.web.toolkit.settings.AppSetting

import grails.events.annotation.Subscriber
import grails.gorm.multitenancy.Tenants
import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j
import org.olf.numgen.*;


/**
 * This service works at the module level, it's often called without a tenant context.
 */
@Slf4j
@Transactional
public class HousekeepingService {

  @Subscriber('okapi:dataload:reference')
  public void onLoadReference (final String tenantId, String value, final boolean existing_tenant, final boolean upgrading, final String toVersion, final String fromVersion) {
    log.debug("ErmHousekeepingService::onLoadReference(${tenantId},${value},${existing_tenant},${upgrading},${toVersion},${fromVersion})");
    final String tenant_schema_id = OkapiTenantResolver.getTenantSchemaName(tenantId)
    try {
      Tenants.withId(tenant_schema_id) {

        // Load default sequences availabe for all installations
        AppSetting.withTransaction {
          [
            [
              code:'openAccess',
              name:'Open Access',
              sequences: [
                [ code:'requestSequence',     'format':'000000000',         'checkDigitAlgo':'EAN13',    'outputTemplate':'oa-${generated_number}-${checksum}' ]
              ]
            ],
            [
              code:'Patron',
              name:'Patron',
              sequences: [
                [ code:'patron',     'format':'000000000',         'checkDigitAlgo':'EAN13',    'outputTemplate':'P${generated_number}-${checksum}' ],
                [ code:'staff',      'format':'000000000',         'checkDigitAlgo':'EAN13',    'outputTemplate':'S${generated_number}-${checksum}' ]
              ]
            ],
          ].each { ng_defn ->
            NumberGenerator ng = NumberGenerator.findByCode(ng_defn.code) ?: new NumberGenerator(code:ng_defn.code, name:ng_defn.name).save(flush:true, failOnError:true);
            ng_defn.sequences.each { seq_defn ->
            }
          }
        }
      }
    }
    catch ( Exception e ) {
      log.error("Problem with load reference",e);
    }
  }
}
